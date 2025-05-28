package com.example.prueba1.ws

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
// Usa el paquete donde se genera tu clase R (namespace en build.gradle)
import com.minka.app.R
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.minka.app.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Servicio en primer plano encargado de mantener viva la conexión WebSocket.
 * 1. Recibe los parámetros de conexión a través del Intent (extras).
 * 2. Arranca en foreground para que Doze no mate la conexión.
 * 3. Guarda los parámetros en SharedPreferences para poder relanzarse
 *    (por ReconnectWorker o BootReceiver) sin perderlos.
 *
 * El listener real lo delega a [WebSocketManager.listener] para propagar
 * eventos a la capa UI.
 */
class WebSocketService : Service() {

    /** SharedPrefs para persistir los parámetros de conexión */
    private val prefs by lazy {
        getSharedPreferences("ws_prefs", Context.MODE_PRIVATE)
    }
       // Recibe la señal de “session ended” para limpiar todo
       private val sessionEndReceiver = object : BroadcastReceiver() {
               override fun onReceive(context: Context, intent: Intent) {
                       if (intent.action == ACTION_SESSION_ENDED) {
                               // 1) Borrar los prefs de conexión
                               prefs.edit().clear().apply()
                               // 2) Parar el servicio y el socket
                               stopSelf()
                           }
                   }
       }

    /** Cliente HTTP con ping y pool configurados */
    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .build()
    }

    private var socket: okhttp3.WebSocket? = null

    /* ---------------- Service lifecycle ---------------- */

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LocalBroadcastManager.getInstance(this)
                   .registerReceiver(sessionEndReceiver, IntentFilter(ACTION_SESSION_ENDED))
        // Si llegan parámetros nuevos, guárdalos
        intent?.extras?.let { b ->
            prefs.edit().apply {
                putString("host",     b.getString("host"))
                putString("clientId", b.getString("clientId"))
                putString("roomId",   b.getString("roomId"))
                putString("password", b.getString("password"))
            }.apply()
        }

        // Arranca el servicio en primer plano (si no lo estaba)
        startForeground(NOTIF_ID, buildNotification("Conectado"))

        // Abre (o re‑abre) el socket
        openSocket()

        // START_STICKY → el sistema intentará recrearlo si lo mata
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Avisar al servidor que nos vamos
        WebSocketManager.sendLeave()
        // Quitar receptor antes de morir
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(sessionEndReceiver)
        socket?.cancel()
        socket = null
        super.onDestroy()
    }

    /* ---------------- Internals ---------------- */

    private fun openSocket() {
        if (socket != null) return  // ya conectado / conectando

        val host     = prefs.getString("host",     null) ?: return
        val clientId = prefs.getString("clientId", null) ?: return
        val roomId   = prefs.getString("roomId",   null) ?: return
        val password = prefs.getString("password", null) ?: return

        // Persistir clientId en DataStore para dispositivos vinculados
        val devicesKey = stringSetPreferencesKey("linked_devices")
        CoroutineScope(Dispatchers.IO).launch {
            applicationContext.dataStore.edit { settings ->
                val current = settings[devicesKey]?.toMutableSet() ?: mutableSetOf()
                current += clientId
                settings[devicesKey] = current
            }
        }

        val url = "ws://$host/ws" +
                "?action=join" +
                "&client_id=$clientId" +
                "&room_id=$roomId" +
                "&password=$password"

        val req = Request.Builder().url(url).build()

        socket = client.newWebSocket(req, WebSocketManager.listener(this))
    }

    /* ---------------- Notificación de servicio ---------------- */

    private fun buildNotification(text: String): Notification {
        val chanId = "ws_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(chanId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    chanId,
                    "Sincronización Minka",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        return NotificationCompat.Builder(this, chanId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // usa cualquier ícono válido
            .setContentTitle("Minka")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_WS_CONNECTED = "com.example.prueba1.ws.ACTION_WS_CONNECTED"
        const val ACTION_WS_DISCONNECTED = "com.example.prueba1.ws.ACTION_WS_DISCONNECTED"
        const val ACTION_SESSION_ENDED    = "com.example.prueba1.ws.ACTION_SESSION_ENDED"
        private const val NOTIF_ID = 1001
    }
}
