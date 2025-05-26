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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
// Usa el paquete donde se genera tu clase R (namespace en build.gradle)
import com.minka.app.R
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

    /** Cliente HTTP con ping y pool configurados */
    private val client by lazy {
        OkHttpClient.Builder()
            // Sends a ping frame every 3 minutes to keep the connection alive.
            // This value might need adjustment based on server-side timeout settings
            // and battery consumption considerations.
            .pingInterval(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            // ConnectionPool(0, ...) means no idle connections are kept in the pool.
            // For a single, persistent WebSocket, this is generally acceptable,
            // as the WebSocket connection itself is long-lived once established.
            // OkHttp's default is 5 idle connections with a 5-minute keep-alive.
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .build()
    }

    private var socket: okhttp3.WebSocket? = null

    private val notificationStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationText = when (intent?.action) {
                ACTION_WS_CONNECTING -> "Connecting to server..."
                ACTION_WS_CONNECTED -> "Connected - Monitoring activity"
                ACTION_WS_DISCONNECTED -> "Connection lost - Reconnecting..."
                else -> null // Or a default text
            }
            notificationText?.let {
                nm.notify(NOTIF_ID, buildNotification(it))
            }
        }
    }

    /* ---------------- Service lifecycle ---------------- */

    override fun onCreate() {
        super.onCreate()
        // Create notification channel (moved from buildNotification to here for one-time setup)
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
        startForeground(NOTIF_ID, buildNotification("Service starting...")) // Initial foreground call

        val intentFilter = IntentFilter().apply {
            addAction(ACTION_WS_CONNECTING)
            addAction(ACTION_WS_CONNECTED)
            addAction(ACTION_WS_DISCONNECTED)
        }
        // Use LocalBroadcastManager for broadcasts within the app
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationStateReceiver, intentFilter)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.extras?.let { b ->
            prefs.edit().apply {
                putString("host",     b.getString("host"))
                putString("clientId", b.getString("clientId"))
                putString("roomId",   b.getString("roomId"))
                putString("password", b.getString("password"))
            }.apply()
        }

        // Ensure the service is in the foreground.
        // The notification text will be updated by broadcasts based on connection state.
        startForeground(NOTIF_ID, buildNotification("Service active...")) // Generic initial text

        // Trigger the connection process and initial state broadcast
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_WS_CONNECTING))
        openSocket()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationStateReceiver)
        socket?.cancel() // Cancel the OkHttp WebSocket
        socket = null    // Clear the local reference
        WebSocketManager.updateSocket(null) // Inform WebSocketManager
        super.onDestroy()
    }

    /* ---------------- Internals ---------------- */

    private fun openSocket() {
        if (socket != null) {
            Log.d("WS_Service", "WebSocket openSocket() called but socket already exists (connecting or connected).")
            return  // ya conectado / conectando
        }

        val host     = prefs.getString("host",     null) ?: run { Log.e("WS_Service", "Host not found in prefs for openSocket"); return }
        val clientId = prefs.getString("clientId", null) ?: run { Log.e("WS_Service", "ClientId not found in prefs for openSocket"); return }
        val roomId   = prefs.getString("roomId",   null) ?: run { Log.e("WS_Service", "RoomId not found in prefs for openSocket"); return }
        val password = prefs.getString("password", null) ?: run { Log.e("WS_Service", "Password not found in prefs for openSocket"); return }

        val url = "ws://$host/ws" +
                "?action=join" +
                "&client_id=$clientId" +
                "&room_id=$roomId" +
                "&password=$password"

        val maskedUrl = url.replaceAfter("password=", "******")
        Log.i("WS_Service", "Opening new WebSocket connection to: $maskedUrl")

        val req = Request.Builder().url(url).build()
        // Create new socket AND immediately update WebSocketManager
        val newSocket = client.newWebSocket(req, WebSocketManager.listener(this))
        this.socket = newSocket // Assign to local property
        WebSocketManager.updateSocket(newSocket) // Update WebSocketManager with the new socket
    }

    /* ---------------- Notificación de servicio ---------------- */

    private fun buildNotification(text: String): Notification {
        val chanId = "ws_channel"
        // Channel creation is now in onCreate

        return NotificationCompat.Builder(this, chanId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // usa cualquier ícono válido
            .setContentTitle("Minka")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001
        const val ACTION_WS_CONNECTING = "com.example.prueba1.ws.ACTION_WS_CONNECTING"
        const val ACTION_WS_CONNECTED = "com.example.prueba1.ws.ACTION_WS_CONNECTED"
        const val ACTION_WS_DISCONNECTED = "com.example.prueba1.ws.ACTION_WS_DISCONNECTED"
    }
}
