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

/**
 * Solo parsea mensajes y notifica a la UI.
 * La conexión la maneja [WebSocketService]; si se cierra, pide
 * reconexión mediante [ReconnectWorker].
 */
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
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d("WS_Manager", "Received message: $text")
            val jsonElem = JsonParser.parseString(text)
            if (jsonElem.isJsonObject) {
                val obj = jsonElem.asJsonObject

                // 1) Si es un "room_created" o "joined", lo ignoramos aquí
                if (obj.has("room_created")) {
                    // mensaje de creación de sala → no es desconexión
                    return
                }
                if (obj.has("event") && obj.get("event").asString == "joined") {
                    // mensaje de emparejado → tampoco es desconexión
                    return
                }

                // 2) Sólo si el info viene con la palabra "desconect" (o la clave que uses en tu servidor)
                if (obj.has("info") && obj.get("info").asString.contains("desconect", ignoreCase = true)) {
                    val info = obj.get("info").asString
                    setShouldReconnect(ctx, false)
                    onError?.invoke(info)
                    LocalBroadcastManager.getInstance(ctx)
                        .sendBroadcast(Intent(WebSocketService.ACTION_SESSION_ENDED))
                    return
                }
            }

            // 3) Por fin los datos de notificación “reales”
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
                ReconnectWorker.enqueue(ctx)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
            Log.w("WS", "Fallo WS: ${t.localizedMessage}")
            onError?.invoke(t.localizedMessage ?: "Error de conexión")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))
            if (shouldReconnect(ctx)) {
                ReconnectWorker.enqueue(ctx)
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
    fun sendNotification(notification: NotificationData): Boolean {
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
    }
}
