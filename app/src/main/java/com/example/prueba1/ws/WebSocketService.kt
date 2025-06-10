// WebSocketService.kt - Versión Final Simplificada

package com.example.prueba1.ws

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.minka.app.NotificationData
import com.minka.app.R
import com.minka.app.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {

    private val prefs by lazy { getSharedPreferences("ws_prefs", Context.MODE_PRIVATE) }

    companion object {
        const val ACTION_WS_CONNECTED = "com.example.prueba1.ws.ACTION_WS_CONNECTED"
        const val ACTION_WS_DISCONNECTED = "com.example.prueba1.ws.ACTION_WS_DISCONNECTED"
        const val ACTION_SESSION_ENDED = "com.example.prueba1.ws.ACTION_SESSION_ENDED"
        const val ACTION_NETWORK_STATUS = "com.example.prueba1.ws.ACTION_NETWORK_STATUS"
        const val ACTION_RECONNECT_RESULT = "com.example.prueba1.ws.ACTION_RECONNECT_RESULT"
        const val EXTRA_MESSAGE = "extra_message"
        private const val NOTIF_ID = 1001
        const val KEY_SHOULD_RECONNECT = "shouldReconnect"
    }

    private val socketLock = Any()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i("WebSocketService", "Red disponible. Intentando reconexión proactiva.")
            sendNetworkStatusBroadcast("Regresó el internet. Intentando reconectar...")
            // CORRECCIÓN: Avisamos que es una reconexión ANTES de abrir el socket.
            WebSocketManager.isReconnecting = true
            openSocket()
        }
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.w("WebSocketService", "Se ha perdido la conexión de red.")
            sendNetworkStatusBroadcast("Se fue el internet.")
        }
    }

    private val connectionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_WS_CONNECTED -> {
                    // Ahora solo enviamos el mensaje a la UI. El mensaje al socket ya se envió desde el Manager.
                    sendReconnectResultBroadcast("Reconexión exitosa")
                }
                ACTION_WS_DISCONNECTED -> {
                    sendReconnectResultBroadcast("Conexión perdida. Se reintentará.")
                    synchronized(socketLock) {
                        Log.w("WebSocketService", "CONEXIÓN PERDIDA: Limpiando variable de socket a null.")
                        socket = null
                    }
                }
            }
        }
    }

    private val sessionEndReceiver = object : BroadcastReceiver() { /* ... sin cambios ... */
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SESSION_ENDED) {
                prefs.edit().clear().apply()
                stopSelf()
            }
        }
    }

    private val dozeReceiver = object : BroadcastReceiver() { /* ... sin cambios ... */
        override fun onReceive(context: Context, intent: Intent) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isDeviceIdleMode) {
                Log.i("WebSocketService", "Entrando en Doze mode.")
                WebSocketManager.sendLeave("doze")
                WebSocketManager.setFallback { notif -> sendViaApi(notif) }
                sendViaApi(NotificationData(info = "DozeMode"))
                socket?.close(1000, "Entering Doze")
                socket = null
            } else {
                Log.i("WebSocketService", "Saliendo de Doze mode.")
                WebSocketManager.clearFallback()
                // CORRECCIÓN: Avisamos que es una reconexión.
                WebSocketManager.isReconnecting = true
                openSocket()
            }
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .build()
    }

    // La llamada a la API ya no es necesaria, pero mantenemos el cliente por si lo usas para otra cosa.
    private val apiClient by lazy { OkHttpClient() }

    private var socket: okhttp3.WebSocket? = null

    override fun onCreate() { /* ... sin cambios ... */
        super.onCreate()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(sessionEndReceiver, IntentFilter(ACTION_SESSION_ENDED))
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_WS_CONNECTED)
            addAction(ACTION_WS_DISCONNECTED)
        }
        lbm.registerReceiver(connectionResultReceiver, intentFilter)
        registerReceiver(dozeReceiver, IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { /* ... sin cambios ... */
        intent?.extras?.let { b ->
            prefs.edit().apply {
                putString("host", b.getString("host"))
                putString("clientId", b.getString("clientId"))
                putString("roomId", b.getString("roomId"))
                putString("password", b.getString("password"))
            }.apply()
        }
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, true).apply()
        startInForeground("Conectado")
        openSocket()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent) { /* ... sin cambios ... */
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, false).apply()
        WebSocketManager.sendLeave("tarea_removida")
        socket?.close(1000, "Tarea removida")
        socket = null
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() { /* ... sin cambios ... */
        super.onDestroy()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) { Log.w("WebSocketService", "Error al desregistrar network callback: ${e.message}") }
        unregisterReceiver(dozeReceiver)
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.unregisterReceiver(sessionEndReceiver)
        lbm.unregisterReceiver(connectionResultReceiver)
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, false).apply()
        WebSocketManager.sendLeave()
        WebSocketManager.updateSocket(null)
        socket?.cancel()
        socket = null
    }

    private fun openSocket() {
        synchronized(socketLock) {
            if (socket != null) {
                Log.d("WebSocketService", "Intento de abrir socket ignorado: ya existe una conexión o intento en curso.")
                return
            }

            val host = prefs.getString("host", null) ?: return
            //val clientId = prefs.getString("clientId", null) ?: return
            //val roomId = prefs.getString("roomId", null) ?: return
            //val password = prefs.getString("password", null) ?: return

            // El operador "?." (safe call) asegura que .trim() solo se llama si el string no es nulo.
            val clientId = prefs.getString("clientId", null)?.trim() ?: return
            val roomId = prefs.getString("roomId", null)?.trim() ?: return
            val password = prefs.getString("password", null)?.trim() ?: return

            // --- LÍNEA DE LOG AÑADIDA ---
            // Aquí mostramos en Logcat los datos que se usarán para la conexión.
            Log.i("WebSocketService", """
                Conectando con los siguientes datos:
                - Client ID: $clientId
                - Room ID:   $roomId
                - Password:  $password 
            """.trimIndent())
            // --- FIN DE LA LÍNEA AÑADIDA ---

            Log.i("WebSocketService", "INTENTANDO ABRIR NUEVA CONEXIÓN WEBSOCKET...")

            val devicesKey = stringSetPreferencesKey("linked_devices")
            CoroutineScope(Dispatchers.IO).launch {
                applicationContext.dataStore.edit { settings ->
                    val current = settings[devicesKey]?.toMutableSet() ?: mutableSetOf()
                    current += clientId
                    settings[devicesKey] = current
                }
            }
            val url = "ws://$host/ws?action=join&client_id=$clientId&room_id=$roomId&password=$password"
            //val url = "ws://$host/ws?client_id=$clientId&action=join&room_id=$roomId&password=$password"
            val req = Request.Builder().url(url).build()
            socket = client.newWebSocket(req, WebSocketManager.listener(this))
            WebSocketManager.updateSocket(socket)
        }
    }

    // --- Funciones de Notificación ---

    private fun sendNetworkStatusBroadcast(message: String) { /* ... sin cambios ... */
        val intent = Intent(ACTION_NETWORK_STATUS).apply { putExtra(EXTRA_MESSAGE, message) }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendReconnectResultBroadcast(message: String) { /* ... sin cambios ... */
        val intent = Intent(ACTION_RECONNECT_RESULT).apply { putExtra(EXTRA_MESSAGE, message) }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // ELIMINADO: La función notifyServerAboutReconnection() ya no es necesaria.

    private fun startInForeground(contentText: String) { /* ... sin cambios ... */
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, buildNotification(contentText), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification(contentText))
        }
    }
    private fun buildNotification(text: String): Notification { /* ... sin cambios ... */
        val chanId = "ws_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(chanId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(chanId, "Sincronización Minka", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, chanId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Minka")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun sendViaApi(notification: NotificationData) { /* ... sin cambios ... */
        val payloadMap = mapOf(
            "client_id" to prefs.getString("clientId", "")!!,
            "room_id"   to prefs.getString("roomId",   "")!!,
            "message"   to notification
        )
        val json = Gson().toJson(payloadMap)
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json)
        val url = "https://${prefs.getString("host", "")}/api/rooms/message"
        val request = Request.Builder().url(url).post(body).build()
        apiClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("WebSocketService", "Fallback API failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.i("WebSocketService", "Fallback API success: ${response.code}")
                response.close()
            }
        })
    }
}