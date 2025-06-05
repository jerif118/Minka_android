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
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
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
import com.google.gson.Gson
import com.minka.app.NotificationData
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
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

    companion object {
        const val ACTION_WS_CONNECTED = "com.example.prueba1.ws.ACTION_WS_CONNECTED"
        const val ACTION_WS_DISCONNECTED = "com.example.prueba1.ws.ACTION_WS_DISCONNECTED"
        const val ACTION_SESSION_ENDED    = "com.example.prueba1.ws.ACTION_SESSION_ENDED"
        private const val NOTIF_ID = 1001
        const val KEY_SHOULD_RECONNECT = "shouldReconnect"
    }

    private fun scheduleReconnectIfNeeded() {
        val shouldReconnect = prefs.getBoolean(KEY_SHOULD_RECONNECT, false)
        if (shouldReconnect) {
            ReconnectWorker.enqueue(applicationContext)
        }
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

       // Recibe la señal de entrada a Doze mode para notificar al servidor
       private val dozeReceiver = object : BroadcastReceiver() {
           override fun onReceive(context: Context, intent: Intent) {
               val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
               if (pm.isDeviceIdleMode) {
                   Log.i("WebSocketService", "Entrando en Doze mode – notificando al servidor")
                   // Notificar por WebSocket
                   WebSocketManager.sendLeave("doze")
                   // Todas las notificaciones se enviarán por REST mientras no haya WebSocket
                   WebSocketManager.setFallback { notif -> sendViaApi(notif) }
                   // Fallback: enviar mensaje por API REST
                   sendViaApi(NotificationData(info = "DozeMode")) // usa el constructor adecuado
                   // Cerrar socket local
                   socket?.close(1000, "Entering Doze")
                   socket = null
               }else{
                   Log.i("WebSocketService", "Saliendo de Doze mode – reconectando")
                   WebSocketManager.clearFallback()
                   openSocket() // <--volver a conectar saliendo del estado doze
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

    // Cliente HTTP para fallback API
    private val apiClient by lazy { OkHttpClient() }

    /** Envía el mensaje vía REST API cuando el socket no está disponible (Doze fallback) */
    private fun sendViaApi(notification: NotificationData) {
        val payloadMap = mapOf(
            "client_id" to prefs.getString("clientId", "")!!,
            "room_id"   to prefs.getString("roomId",   "")!!,
            "message"   to notification
        )
        val json = Gson().toJson(payloadMap)
        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            json
        )
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

    private var socket: okhttp3.WebSocket? = null

    /* ---------------- Service lifecycle ---------------- */

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LocalBroadcastManager.getInstance(this)
                   .registerReceiver(sessionEndReceiver, IntentFilter(ACTION_SESSION_ENDED))
        // Registrar receptor de Doze
        registerReceiver(dozeReceiver, IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        // Si llegan parámetros nuevos, guárdalos
        intent?.extras?.let { b ->
            prefs.edit().apply {
                putString("host",     b.getString("host"))
                putString("clientId", b.getString("clientId"))
                putString("roomId",   b.getString("roomId"))
                putString("password", b.getString("password"))
            }.apply()
        }

        // Marcar que sí permitimos reconectar en caso de fallo
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, true).apply()

        // Arranca el servicio en primer plano (si no lo estaba)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                buildNotification("Conectado"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, buildNotification("Conectado"))
        }

        openSocket()     // fallback se activará sólo cuando entremos en Doze

        // START_STICKY → el sistema intentará recrearlo si lo mata
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent) {
        // Desconexión voluntaria por "swipe": no reconectar
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, false).apply()
        WebSocketManager.sendLeave("tarea_removida")
        socket?.close(1000, "Tarea removida")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Indicar que no queremos reconectar tras cierre voluntario
        prefs.edit().putBoolean(KEY_SHOULD_RECONNECT, false).apply()
        // Avisar al servidor que nos vamos
        WebSocketManager.sendLeave()
        WebSocketManager.updateSocket(null)
        unregisterReceiver(dozeReceiver)
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
        WebSocketManager.updateSocket(socket)
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

}
