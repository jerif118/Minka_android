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

    fun sendLeave(): Boolean {
        val s = socket ?: return false
              val payload = gson.toJson(mapOf("action" to "leave"))
              return s.send(payload)
    }


    var onNotification: ((NotificationData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun listener(ctx: Context) = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, resp: Response) {
            Log.i("WS", "Conectado: $resp")
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(WebSocketService.ACTION_WS_CONNECTED))
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
