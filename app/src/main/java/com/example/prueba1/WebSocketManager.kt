package com.minka.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.minka.app.NotificationData
import okhttp3.*

object WebSocketManager {
    private val client = OkHttpClient()
    private val gson   = Gson()
    private var socket: WebSocket? = null

    // Indicador de estado de conexión
    private var isConnected = false

    var onNotification: ((NotificationData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect(
        hostServidor: String,
        clientId: String,
        roomId: String,
        password: String
    ) {
        isConnected = false

        val url = "ws://$hostServidor/ws" +
                "?action=join" +
                "&client_id=$clientId" +
                "&room_id=$roomId" +
                "&password=$password"

        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, resp: Response) {
                    Log.i("WS", "Conectado: $resp")
                    isConnected = true
                }

                override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                    isConnected = false
                    Handler(Looper.getMainLooper()).post {
                        onError?.invoke(t.localizedMessage)
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    Log.i("WS", "Cerrado: $code / $reason")
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val data = gson.fromJson(text, NotificationData::class.java)
                        onNotification?.invoke(data)
                    } catch (e: Exception) {
                        onError?.invoke("JSON inválido: ${e.message}")
                    }
                }
            }
        )
    }

    fun disconnect() {
        socket?.close(1000, "App cerrada")
        socket = null
        isConnected = false
    }

    fun sendNotification(notification: NotificationData) {
        if (isConnected && socket != null) {
            Log.d("WS‑DBG", "sendNotification llamado: isConnected=$isConnected, socket=$socket")
            // 1) Envolvemos la notificación bajo "message"
            val wrapper = mapOf("message" to notification)
            val json = gson.toJson(wrapper)
            // 2) Enviamos ese JSON
            val ok = socket!!.send(json)
            Log.d("WS‑DBG", "→ Intento de envío: $json")
            Log.d("WS‑DBG", "→ send() devolvió: $ok")
            Log.d("WS", "Notificación enviada (wrapper): $json → éxito? $ok")
        } else {
            Log.w("WS", "Ignorado (no conectado): ${gson.toJson(notification)}")
        }
    }

}

