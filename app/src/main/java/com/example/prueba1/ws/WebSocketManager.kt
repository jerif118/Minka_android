package com.example.prueba1.ws

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    var onNotification: ((NotificationData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun listener(ctx: Context) = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, resp: Response) {
            Log.i("WS", "Conectado: $resp")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_CONNECTED))
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d("WS_Manager", "Received message: $text") // Log incoming message
            try {
                val data = gson.fromJson(text, NotificationData::class.java)
                onNotification?.invoke(data)
            } catch (e: Exception) {
                Log.e("WS_Manager", "JSON parsing error: ${e.message} for message: $text", e) // Log error and raw message
                onError?.invoke("JSON inválido: ${e.message}")
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.i("WS", "Cerrado: $code / $reason")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))
            ReconnectWorker.enqueue(ctx)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
            Log.w("WS", "Fallo WS: ${t.localizedMessage}")
            onError?.invoke(t.localizedMessage ?: "Error de conexión")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_DISCONNECTED))
            ReconnectWorker.enqueue(ctx)
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
            Log.w("WS_Manager", "sendNotification called but socket is null. Message not sent.")
            return false
        }
        val jsonPayload = gson.toJson(mapOf("message" to notification))
        Log.d("WS_Manager", "Sending JSON: $jsonPayload") // Log the outgoing JSON
        return s.send(jsonPayload)
    }
}
