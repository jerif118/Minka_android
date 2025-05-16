package com.minka.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.minka.app.NotificationData
import android.content.Context
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
        ctx: Context,
        roomId: String? = null,
        pairingToken: String? = null

    ) {
        val clientId = SecureStore.getOrCreateClientId(ctx)
        isConnected = false

        val url = if (pairingToken != null && roomId != null) {
            // ① Fase de emparejamiento (tras escanear QR)
            "ws://$hostServidor/ws" +
                    "?pairing_token=$pairingToken&client_id=$clientId&room_id=$roomId"
        } else {
            // ② Reconexión normal
            "ws://$hostServidor/ws"
        }

        val reqBuilder = Request.Builder().url(url)

        // Añadir la cookie con el token largo, si existe
        SecureStore.loadToken(ctx)?.let { jwt ->
            reqBuilder.addHeader("Cookie", "minka_session=$jwt")
        }

        socket = client.newWebSocket(reqBuilder.build(), object : WebSocketListener() {

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val map = gson.fromJson(text, Map::class.java)
                    when (map["event"]) {
                        "paired" -> {
                            val jwt = map["token"] as? String
                            jwt?.let {
                                SecureStore.saveToken(ctx, it)
                                ws.close(1000, "paired, reconnecting")
                                connect(hostServidor, ctx) // reconnect with cookie
                            }
                        }
                        else -> {
                            // mensaje de notificación
                            map["message"]?.let { payload ->
                                val jsonPayload = gson.toJson(payload)
                                val notification = gson.fromJson(jsonPayload, NotificationData::class.java)
                                onNotification?.invoke(notification)
                            }
                        }
                    }
                } catch (e: Exception) {
                    onError?.invoke("WS parse error: ${e.localizedMessage}")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                if (code == 4003 || code == 4005) {
                    // token vencido o inválido
                    SecureStore.clearToken(ctx)
                }
            }
        })
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

