package com.example.prueba1.ws

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.minka.app.NotificationData
import kotlinx.coroutines.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Gestiona la lógica de mensajes y la estrategia de reconexión del WebSocket.
 */

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

object WebSocketManager {

    @Volatile private var isManualShutdown = false

    private const val TAG = "WebSocketManager"
    private const val ROOM_FULL_RETRY_TAG = "RoomFullRetryLogic"
    // --- Variables de estado y reintentos ---
    private val gson = Gson()
    private var socket: WebSocket? = null
    var isReconnecting: Boolean = false

    private var roomFullRetryCount = 0
    private var roomFullRetryJob: Job? = null
    // <-- CAMBIO: Ajustado a 2 para permitir un total de 3 intentos (1 inicial + 2 reintentos).
    private const val MAX_ROOM_FULL_RETRIES = 2
    @Volatile private var isClosingForRoomFull = false

    private var generalRetryCount = 0
    private var generalRetryJob: Job? = null
    private const val MAX_GENERAL_RETRIES = 3

    // --- Manejadores de eventos públicos ---
    var onNotification: ((NotificationData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onFallback: ((NotificationData) -> Unit)? = null
    fun setFallback(cb: ((NotificationData) -> Unit)?) { onFallback = cb }
    fun clearFallback() { onFallback = null }

    internal fun updateSocket(ws: WebSocket?) { socket = ws }

    fun sendLeave(reason: String = ""): Boolean {
        val s = socket ?: return false
        val payload = gson.toJson(mapOf("action" to "leave", "reason" to reason.ifBlank { null }))
        Log.d(TAG, "Enviando leave payload: $payload")
        return s.send(payload)
    }

    private const val PREFS_NAME = "ws_prefs"
    private const val KEY_SHOULD_RECONNECT = "should_reconnect"
    private fun setShouldReconnect(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOULD_RECONNECT, value).apply()
    }
    private fun shouldReconnect(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SHOULD_RECONNECT, true)
    }

    fun prepareForManualShutdown() {
        Log.i(TAG, "Preparando para un cierre manual de la sesión.")
        isManualShutdown = true
    }

    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME, // Puedes usar el mismo nombre de archivo
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- LISTENER PRINCIPAL DEL WEBSOCKET ---
    fun listener(ctx: Context) = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, resp: Response) {
            Log.i(TAG, "Conectado: $resp")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_CONNECTED))
            setShouldReconnect(ctx, true)
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "↓ Mensaje recibido del servidor: $text")

            val jsonElem = try { JsonParser.parseString(text) } catch (e: Exception) {
                Log.w(TAG, "Mensaje recibido no es un JSON válido: $text"); return
            }

            if (!jsonElem.isJsonObject) { Log.w(TAG, "Mensaje no es un JSON Object."); return }
            val obj = jsonElem.asJsonObject

            fun saveToken(token: String?) {
                if (token == null) {
                    Log.e(TAG, "Intento de guardar un token nulo. Ignorando.")
                    return
                }
                try {
                    val securePrefs = getSecurePrefs(ctx) // <-- Obtenemos la instancia segura
                    securePrefs.edit()
                        .putString("jwt_token", token)
                        .apply() // Se guarda el token

                    Log.i(TAG, "Token guardado/actualizado de forma segura.")

                    // !!! DESCOMENTA Y VERIFICA ESTA LÍNEA AL CORRER LA APP !!!
                    val retrievedToken = securePrefs.getString("jwt_token", null)
                    Log.d(TAG, "VERIFICACIÓN: Token recuperado INMEDIATAMENTE después de guardar: ${retrievedToken?.take(10)}...${retrievedToken?.takeLast(10)} (truncated)")
                    if (retrievedToken == null) {
                        Log.e(TAG, "!!! ADVERTENCIA CRÍTICA: El token fue guardado, pero no se pudo recuperar INMEDIATAMENTE. Puede haber un problema con EncryptedSharedPreferences. !!!")
                    }


                } catch (e: Exception) {
                    Log.e(TAG, "Error al guardar el token de forma segura.", e)
                }
            }

            if (obj.has("code") && obj.get("code").asString == "ROOM_FULL") {
                Log.w(ROOM_FULL_RETRY_TAG, "-> ¡RECHAZO! El servidor respondió con 'ROOM_FULL'.")
                isClosingForRoomFull = true
                ws.close(1001, "Client closing due to ROOM_FULL")
                return
            }

            // Lógica para conexión exitosa (joined/room_created)
            if (obj.has("event")) {
                val event = obj.get("event").asString
                when (event) {
                    "jwt_updated", "token_actualizado" -> { // <-- Ahora captura ambos eventos
                        Log.i(TAG, "El servidor ha enviado/actualizado un token JWT.")
                        saveToken(obj.get("jwt_token")?.asString) //
                        // No retornamos aquí para permitir que se procese 'joined_room' si viene inmediatamente después
                    }
                    "joined_room" -> { // El evento real que recibes cuando te unes.
                        Log.i(TAG, "✅ El servidor confirmó la unión a la sala. La sesión es válida. Reseteando todos los contadores de reintentos.")
                        // Asegurarse de que el token también se guarde si viene con este evento
                        // Aunque tu log muestra que "jwt_updated" viene *antes*, es buena práctica
                        // intentar guardarlo también aquí si lo incluye.
                        saveToken(obj.get("jwt_token")?.asString) //

                        roomFullRetryCount = 0 //
                        roomFullRetryJob?.cancel() //
                        generalRetryCount = 0 //
                        generalRetryJob?.cancel() //


                        if (isReconnecting) { //
                            Log.d(TAG, "Enviando evento 'client_reconnected' post-confirmación.") //
                            try {
                                val prefs = ctx.getSharedPreferences("ws_prefs", Context.MODE_PRIVATE) //
                                val clientId = prefs.getString("clientId", "unknown") //
                                val reconnectPayload = mapOf(
                                    "event" to "client_reconnected", "clientId" to clientId,
                                    "message" to "El cliente $clientId se ha reconectado exitosamente."
                                )
                                ws.send(gson.toJson(reconnectPayload)) //
                            } catch (e: Exception) { Log.e(TAG, "Error al enviar mensaje de reconexión.", e) } //
                            isReconnecting = false //
                        }
                        return //
                    }
                    // Puedes añadir otros eventos aquí si los necesitas
                }
            }

            // Bloque para manejar la actualización del token
            if (obj.has("event") && obj.get("event").asString == "token_actualizado") {
                Log.i(TAG, "El servidor ha enviado un token actualizado.")

                // Extraer y guardar el nuevo token de forma segura
                saveToken(obj.get("jwt_token")?.asString)
                return
            }

            // Lógica para fin de sesión explícito
            if (obj.has("info") && obj.get("info").asString.contains("desconect", ignoreCase = true)) {
                val info = obj.get("info").asString
                setShouldReconnect(ctx, false)
                onError?.invoke(info)
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_SESSION_ENDED))
                return
            }

            // Procesamiento de notificaciones normales
            try {
                onNotification?.invoke(gson.fromJson(text, NotificationData::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear JSON de notificación: ${e.message}", e)
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "🔌 CONEXIÓN CERRADA: Código=$code, Razón='$reason'")

            if (isManualShutdown) {
                isManualShutdown = false // Reseteamos la bandera para la próxima sesión
                Log.i(TAG, "Cierre manual detectado. No se enviará broadcast de desconexión.")
                return // IMPORTANTE: Salimos de la función aquí.
            }

            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))

            if (isClosingForRoomFull) {
                isClosingForRoomFull = false
                Log.i(ROOM_FULL_RETRY_TAG, "Cierre por ROOM_FULL. Activando lógica de reintentos específica.")
                handleRoomFullRetry(ctx)
            } else if (shouldReconnect(ctx)) {
                // ESTA ES LA LÍNEA CLAVE
                Log.i(TAG, "-> Decisión: Cierre inesperado. Activando lógica de reconexión general con corutinas.")
                handleGeneralReconnect(ctx)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
            Log.e(TAG, "FALLO DE CONEXIÓN: ${t.javaClass.simpleName} - ${t.message}", t)

            if (isManualShutdown) {
                isManualShutdown = false // Reseteamos la bandera
                Log.i(TAG, "Fallo durante cierre manual detectado. No se enviará broadcast de desconexión.")
                return // IMPORTANTE: Salimos de la función aquí.
            }

            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))

            if (shouldReconnect(ctx)) {
                // ESTA ES LA OTRA LÍNEA CLAVE
                Log.i(TAG, "-> Decisión: Fallo detectado. Activando lógica de reconexión general con corutinas.")
                handleGeneralReconnect(ctx)
            }
        }
    }

    // --- LÓGICAS DE REINTENTO PERSONALIZADAS ---

    private fun handleGeneralReconnect(ctx: Context) {
        generalRetryJob?.cancel()
        generalRetryJob = CoroutineScope(Dispatchers.IO).launch {
            if (generalRetryCount > 0) {
            }

            val delayMillis = when (generalRetryCount) {
                0 -> 3_000L
                1 -> 10_000L
                2 -> 20_000L
                else -> -1L
            }

            if (delayMillis == -1L) {
                Log.e(TAG, "LÍMITE DE REINTENTOS GENERALES ($MAX_GENERAL_RETRIES) ALCANZADO. Rindiéndose.")
                generalRetryCount = 0
                return@launch
            }

            Log.i(TAG, "--> Planificando intento de reconexión #${generalRetryCount + 1} de $MAX_GENERAL_RETRIES")
            Log.i(TAG, "    Esperando ${delayMillis / 1000} segundos...")
            delay(delayMillis)

            if (!isNetworkAvailable(ctx)) {
                Log.w(TAG, "    Intento #${generalRetryCount + 1} abortado. Aún no hay conexión a internet. Se re-evaluará en el próximo ciclo de fallo.")
                return@launch
            }

            generalRetryCount++
            Log.i(TAG, "    Ejecutando intento #$generalRetryCount: Solicitando al servicio que se reconecte.")
            WebSocketService.requestReconnect(ctx)
        }
    }

    private fun handleRoomFullRetry(ctx: Context) {
        roomFullRetryJob?.cancel()
        roomFullRetryJob = CoroutineScope(Dispatchers.IO).launch {
            // Se incrementa el contador *antes* de decidir qué hacer.
            // La primera vez que esta función corre, `roomFullRetryCount` será 1.
            roomFullRetryCount++

            // Si el número de reintentos supera el máximo, nos rendimos.
            // Con MAX = 2, se rendirá cuando roomFullRetryCount llegue a 3.
            if (roomFullRetryCount > MAX_ROOM_FULL_RETRIES) {
                Log.e(ROOM_FULL_RETRY_TAG, "LÍMITE DE REINTENTOS ($MAX_ROOM_FULL_RETRIES) para ROOM_FULL alcanzado. Rindiéndose.")
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_SESSION_ENDED))
                roomFullRetryCount = 0 // Reseteamos para el futuro
                return@launch
            }

            // Determinamos el tiempo de espera según el número de reintento.
            val delayMillis = when (roomFullRetryCount) {
                1 -> 10_000L // 1er reintento (2º intento total): Espera 10 segundos.
                2 -> 20_000L // 2º reintento (3er intento total): Espera 20 segundos.
                else -> 0L // No debería ocurrir con la lógica actual, pero es un fallback seguro.
            }

            // Total de intentos = 1 (el inicial) + MAX_ROOM_FULL_RETRIES (2) = 3
            val totalAttempts = MAX_ROOM_FULL_RETRIES + 1
            val currentAttempt = roomFullRetryCount + 1

            Log.i(ROOM_FULL_RETRY_TAG, "--> [INTENTO POR ROOM_FULL: $currentAttempt de $totalAttempts]")
            Log.i(ROOM_FULL_RETRY_TAG, "    Esperando ${delayMillis / 1000} segundos antes del próximo intento...")
            delay(delayMillis)

            Log.i(ROOM_FULL_RETRY_TAG, "    Ejecutando reintento #$roomFullRetryCount.")
            WebSocketService.requestReconnect(ctx)
        }
    }

    // --- ENVÍO DE NOTIFICACIONES ---

    fun sendNotification(notification: NotificationData): Boolean {
        val s = socket ?: run {
            Log.w(TAG, "WebSocket is null; enviando por fallback REST")
            onFallback?.invoke(notification)
            return false
        }
        val jsonPayload = gson.toJson(mapOf("message" to notification))

        // ▼ LÍNEA AGREGADA PARA VER EL PAYLOAD ▼
        Log.d(TAG, "Payload JSON a enviar: $jsonPayload")

        Log.d(TAG, "↑ ENVIANDO MENSAJE AL SERVIDOR: ${notification.title}")
        return s.send(jsonPayload)
    }
}