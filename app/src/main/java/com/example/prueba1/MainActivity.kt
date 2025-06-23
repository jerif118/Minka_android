package com.minka.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.compose.rememberNavController
import com.example.prueba1.ui.theme.Prueba1Theme
import com.example.prueba1.ws.WebSocketManager
import com.example.prueba1.ws.WebSocketService
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import androidx.datastore.preferences.core.stringSetPreferencesKey

data class QrInfo(val room_id: String, val password: String)

class MainActivity : ComponentActivity() {

    private lateinit var qrScanner: QrScanner

    // <-- 1. OBTENEMOS EL VIEWMODEL A NIVEL DE ACTIVIDAD -->
    // Esto nos permite acceder a 'vm' desde cualquier parte de la Activity,
    // incluido el BroadcastReceiver.
    private val vm: NotificationViewModel by viewModels()

    // <-- 2. DEFINIMOS EL "OYENTE" DE CAMBIOS DE CONEXIÓN -->
    private val connectionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Cuando recibimos una señal, actualizamos el ViewModel.
            // La UI reaccionará automáticamente a este cambio.
            when (intent?.action) {
                WebSocketService.ACTION_WS_CONNECTED -> {
                    vm.updateConnectionStatus(ConnectionStatus.CONNECTED)
                    Log.d("MainActivity", "Receiver: Conexión WebSocket establecida.")
                }
                WebSocketService.ACTION_WS_DISCONNECTED -> {
                    vm.updateConnectionStatus(ConnectionStatus.DISCONNECTED)
                    Log.d("MainActivity", "Receiver: Conexión WebSocket perdida.")
                }
                WebSocketService.ACTION_SESSION_ENDED -> {
                    vm.updateConnectionStatus(ConnectionStatus.INITIAL)
                    Log.d("MainActivity", "Receiver: Sesión finalizada.")
                }
            }
        }
    }


    companion object {
        private const val REQ_DATA_SYNC = 2002
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    private fun toggleNotificationListenerService() {
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun requestDataSyncPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 34 &&
            checkSelfPermission(
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                REQ_DATA_SYNC
            )
        }
    }

    /** Arranca el WebSocketService solo si ya existen credenciales guardadas */
    private fun ensureWebSocketServiceRunning() {
        val prefs = getSharedPreferences("ws_prefs", Context.MODE_PRIVATE)
        val host = prefs.getString("host", null)
        val clientId = prefs.getString("clientId", null)
        if (!host.isNullOrBlank() && !clientId.isNullOrBlank()) {
            com.example.prueba1.ws.WebSocketService.requestReconnect(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ───────────────────────── PERMISOS ───────────────────────── */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        requestDataSyncPermissionIfNeeded()

        if (isNotificationServiceEnabled()) {
            toggleNotificationListenerService()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage("Para recibir notificaciones de pagos, debes habilitar el acceso a notificaciones")
                .setPositiveButton("Configurar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intentOpt = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intentOpt)
        }

        qrScanner = QrScanner(this)
        enableEdgeToEdge()

        WebSocketManager.onError = { errorMessage ->
            runOnUiThread {
                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        }


        setContent {
            Prueba1Theme {
                val nav = rememberNavController()

                // Ya no necesitamos 'val vm: NotificationViewModel = viewModel()' aquí,
                // porque ya lo tenemos como una propiedad de la Activity.

                DisposableEffect(Unit) {
                    MyNotificationListenerService.notificationListener = vm::addNotification
                    onDispose {
                        MyNotificationListenerService.notificationListener = null
                    }
                }
                MainScreen(
                    navController = nav,
                    vm = vm, // Usamos la instancia de la Activity.
                    onOpenCameraClicked = {
                        lifecycleScope.launch {
                            val devicesKey = stringSetPreferencesKey("linked_devices")
                            val currentDevices = dataStore.data.first()[devicesKey] ?: emptySet()

                            if (currentDevices.isNotEmpty()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Ya tienes un dispositivo vinculado. Desvincula el actual para agregar uno nuevo.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                qrScanner.initiateQrScan()
                            }
                        }
                    }
                )
            }
        }
    }

    // <-- 3. REGISTRAMOS EL "OYENTE" CUANDO LA APP ES VISIBLE -->
    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter().apply {
            addAction(WebSocketService.ACTION_WS_CONNECTED)
            addAction(WebSocketService.ACTION_WS_DISCONNECTED)
            addAction(WebSocketService.ACTION_SESSION_ENDED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionStatusReceiver, intentFilter)
    }

    // <-- 4. DEJAMOS DE ESCUCHAR CUANDO LA APP NO ES VISIBLE -->
    // Esto es crucial para ahorrar batería y evitar errores.
    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionStatusReceiver)
    }

    override fun onStart() {
        super.onStart()
        ensureWebSocketServiceRunning()   // solo cuando la app entra en foreground
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        qrScanner.handleResult(requestCode, resultCode, data)?.let { contents ->

            Log.d("QrScanDebug", "Contenido crudo del QR recibido: '$contents'")

            lifecycleScope.launch {
                try {
                    val devicesK = stringSetPreferencesKey("linked_devices")
                    val currentDevices = applicationContext.dataStore.data.first()[devicesK] ?: emptySet()

                    if (currentDevices.isNotEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Ya existe una sesión activa. Desvincula el dispositivo actual primero.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val info = Gson().fromJson(contents, QrInfo::class.java)
                        val clientId = "mobile-${UUID.randomUUID()}"
                        val svc = Intent(
                            this@MainActivity,
                            WebSocketService::class.java
                        ).apply {
                            putExtra("host", "ws.checkealoya.com")
                            //putExtra("host", "ws.checkealoya.com")
                            putExtra("clientId", clientId)
                            putExtra("roomId", info.room_id)
                            putExtra("password", info.password)
                        }
                        startForegroundService(svc)

                        Toast.makeText(
                            this@MainActivity,
                            "Conectando a sala: ${info.room_id}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "QR inválido\nContenido leído:\n$contents",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                    Log.e("QrScanError", "Error al procesar el QR: ${e.message}")
                }
            }
        }
    }
}