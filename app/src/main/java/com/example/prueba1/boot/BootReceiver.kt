package com.example.prueba1.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prueba1.ws.WebSocketService

/**
 * Se ejecuta tras el reinicio del dispositivo para relanzar
 * el servicio en primer plano que mantiene la conexión WebSocket.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // Inicia el servicio que mantiene vivo el WebSocket
            val svc = Intent(context, WebSocketService::class.java)
            context.startForegroundService(svc)
        }
    }
}
