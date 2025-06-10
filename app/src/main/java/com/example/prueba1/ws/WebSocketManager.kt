package com.example.prueba1.ws

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.minka.app.NotificationData
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Solo parsea mensajes y notifica a la UI.
 * La conexión la maneja [WebSocketService]; si se cierra, pide
 * reconexión mediante [ReconnectWorker].
 */

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

object WebSocketManager {

    private val gson = Gson()

    /** Referencia al WebSocket activo (la actualiza WebSocketService) */
    private var socket: WebSocket? = null

    /**
     * Callback invoked when WebSocket is unavailable and a fallback (e.g., REST API) should be used.
     * The service should assign this to send via HTTP when in Doze mode.
     */
    private var onFallback: ((NotificationData) -> Unit)? = null

    /**
     * Registra (o elimina) la rutina que se usará cuando el WebSocket no esté
     * disponible (p.ej. Doze).  Pasa `null` para desactivar.
     */
    var isReconnecting: Boolean = false

    fun setFallback(cb: ((NotificationData) -> Unit)?) {
        onFallback = cb
    }

    /** Limpia la rutina alternativa, volviendo a modo WebSocket puro. */
    fun clearFallback() {
        onFallback = null
    }

    /**
     * Envía al servidor la acción "leave" con un motivo opcional.
     * @param reason Texto que explica por qué se desconecta (ej. "doze").
     */
    fun sendLeave(reason: String = ""): Boolean {
        val s = socket ?: run {
            Log.w("WS_Manager", "sendLeave llamado pero socket es null")
            return false
        }
        // Construir el payload con motivo si se proporcionó
        val payloadMap = mutableMapOf<String, Any>("action" to "leave")
        if (reason.isNotBlank()) {
            payloadMap["reason"] = reason
        }
        val payload = gson.toJson(payloadMap)
        Log.d("WS_Manager", "Enviando leave payload: $payload")
        return s.send(payload)
    }

    private const val PREFS_NAME = "ws_prefs"
    private const val KEY_SHOULD_RECONNECT = "should_reconnect"

    private fun setShouldReconnect(ctx: Context, value: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, value).apply()
    }

    private fun shouldReconnect(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOULD_RECONNECT, true)
    }

    var onNotification: ((NotificationData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun listener(ctx: Context) = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, resp: Response) {
            Log.i("WS", "Conectado: $resp")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_CONNECTED))
            setShouldReconnect(ctx, true)

            // 2. Solo enviamos el mensaje si 'isReconnecting' es verdadero.
            if (isReconnecting) {
                try {
                    val prefs = ctx.getSharedPreferences("ws_prefs", Context.MODE_PRIVATE)
                    val clientId = prefs.getString("clientId", "unknown")
                    val reconnectPayload = mapOf(
                        "event" to "client_reconnected",
                        "clientId" to clientId,
                        "message" to "El cliente $clientId se ha reconectado."
                    )
                    val jsonPayload = gson.toJson(reconnectPayload)
                    if (ws.send(jsonPayload)) {
                        Log.i("WS_Manager", "Mensaje de reconexión enviado exitosamente: $jsonPayload")
                    } else {
                        Log.w("WS_Manager", "Falló el envío del mensaje de reconexión.")
                    }
                } catch (e: Exception) {
                    Log.e("WS_Manager", "Error al construir o enviar el mensaje de reconexión.", e)
                }
            }
            // 3. Reseteamos la bandera después de cada conexión exitosa.
            isReconnecting = false
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d("WS_Manager", "Received message: $text")
            val jsonElem = JsonParser.parseString(text)
            if (jsonElem.isJsonObject) {
                val obj = jsonElem.asJsonObject
                if (obj.has("room_created") || (obj.has("event") && obj.get("event").asString == "joined")) {
                    return
                }
                if (obj.has("info") && obj.get("info").asString.contains("desconect", ignoreCase = true)) {
                    val info = obj.get("info").asString
                    setShouldReconnect(ctx, false)
                    onError?.invoke(info)
                    LocalBroadcastManager.getInstance(ctx)
                        .sendBroadcast(Intent(WebSocketService.ACTION_SESSION_ENDED))
                    return
                }
            }
            try {
                val data = gson.fromJson(text, NotificationData::class.java)
                onNotification?.invoke(data)
            } catch (e: Exception) {
                Log.e("WS_Manager", "JSON parsing error: ${e.message} for message: $text", e)
                onError?.invoke("JSON inválido: ${e.message}")
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.i("WS", "Cerrado: $code / $reason")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))
            if (shouldReconnect(ctx)) {
                Log.i("WS", "Conexión cerrada. Programando ReconnectWorker con backoff.")
                // ## CAMBIO 1 ##
                enqueueWithBackoff(ctx)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
            Log.w("WS", "Fallo WS: ${t.localizedMessage}")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))
            if (isNetworkAvailable(ctx)) {
                Log.i("WS", "Hay internet pero la conexión falló. Programando ReconnectWorker con backoff.")
                onError?.invoke("No se pudo conectar al servidor. Reintentando en segundo plano...")
                if (shouldReconnect(ctx)) {
                    // ## CAMBIO 1 ##
                    enqueueWithBackoff(ctx)
                }
            } else {
                Log.i("WS", "No hay internet. Esperando a que la red vuelva para reintentar.")
                onError?.invoke("Sin conexión a internet. Se reintentará cuando vuelva la red.")
            }
        }
    }

    /* ---------------------------------------------------------------- */
    /*   API para que otras capas envíen mensajes a través del socket   */
    /* ---------------------------------------------------------------- */

    /**
     * Actualiza la referencia al socket.  Sólo debe llamarlo
     * [WebSocketService] cuando crea o destruye la conexión.
     */
    internal fun updateSocket(ws: WebSocket?) {
        socket = ws
    }

    /**
     * Envía una notificación al servidor.  Devuelve true si se pudo
     * enviar, false si no hay conexión.
     */
    /*fun sendNotification(notification: NotificationData): Boolean {
        val s = socket ?: run {
            Log.w("WS_Manager", "WebSocket is null, invoking fallback for notification")
            onFallback?.invoke(notification)
            return false
        }
        val jsonPayload = gson.toJson(mapOf("message" to notification))
        Log.d("WS_Manager", "–> Invocando a sendNotification con payload: $jsonPayload")
        val sent = s.send(jsonPayload)
        Log.d("WS_Manager", "   …s.send() devolvió: $sent")
        return sent


    }*/
    fun sendNotification(notification: NotificationData): Boolean {
        val s = socket ?: run {
            Log.w("WS_Manager", "WebSocket is null; enviando por REST")
            onFallback?.invoke(notification)
            return false
        }
        val jsonPayload = gson.toJson(mapOf("message" to notification))
        Log.d("WS_Manager", "--> sendNotification payload: $jsonPayload")
        val sent = s.send(jsonPayload)
        Log.d("WS_Manager", "   …s.send() devolvió: $sent")
        if (!sent) {
            Log.w("WS_Manager", "WebSocket send falló; enviando por REST")
            onFallback?.invoke(notification)
        }
        return sent
    }

    fun enqueueWithBackoff(ctx: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val reconnectRequest = OneTimeWorkRequestBuilder<ReconnectWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                // ## CAMBIO 2 ##
                WorkRequest.MIN_BACKOFF_MILLIS, // Empezar con 10 segundos
                TimeUnit.MILLISECONDS
            )
            .build()

        Log.i("WS_Manager", "Encolando trabajo de reconexión único con política de backoff.")
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "ws_reconnect",
            ExistingWorkPolicy.REPLACE,
            reconnectRequest
        )
    }

}
