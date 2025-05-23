package com.example.prueba1.ws

import android.content.Context
import android.util.Log
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
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                val data = gson.fromJson(text, NotificationData::class.java)
                onNotification?.invoke(data)
            } catch (e: Exception) {
                onError?.invoke("JSON inválido: ${e.message}")
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.i("WS", "Cerrado: $code / $reason")
            ReconnectWorker.enqueue(ctx)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
            Log.w("WS", "Fallo WS: ${t.localizedMessage}")
            onError?.invoke(t.localizedMessage ?: "Error de conexión")
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
        val s = socket ?: return false
        val json = gson.toJson(mapOf("message" to notification))
        return s.send(json)
    }
}
