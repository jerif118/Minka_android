package com.minka.app

import com.minka.app.BuildConfig
import android.Manifest
import androidx.activity.viewModels
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.prueba1.NotificationViewModel
import com.example.prueba1.ws.WebSocketManager
import com.example.prueba1.ws.WebSocketService
import com.google.gson.Gson
import java.util.*

data class QrInfo(val room_id: String, val password: String)

class MainActivity : ComponentActivity() {

    private val vm: NotificationViewModel by viewModels()
    private lateinit var qrScanner: QrScanner
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ───────────────────────── PERMISO ANDROID 13+ ───────────────────────── */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001                                  // request‑code arbitrario
            )
        }

        /* ─────────────────────────────────────────────────────────────────────── */
        if (isNotificationServiceEnabled()) {
            toggleNotificationListenerService()
        } else {
            // Mostrar diálogo pidiendo al usuario que habilite el servicio
            AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage("Para recibir notificaciones de pagos, debes habilitar el acceso a notificaciones")
                .setPositiveButton("Configurar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        qrScanner = QrScanner(this)
        enableEdgeToEdge()
        // Configura los callbacks del WebSocketManager aquí
        WebSocketManager.onNotification = { notificationData ->
            // This will be executed when a notification is received from the server
            vm.addNotification(notificationData)
        }

        WebSocketManager.onError = { errorMessage ->
            runOnUiThread {
                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        }


        setContent {
            Theme {
                val nav = rememberNavController()
                // val vm: NotificationViewModel = viewModel() // vm is now a class member

                DisposableEffect(Unit) {
                    MyNotificationListenerService.notificationListener = vm::addNotification
                    onDispose {
                        MyNotificationListenerService.notificationListener = null
                    }
                }

                MainScreen(
                    navController = nav,
                    vm           = vm,
                    onOpenCameraClicked  = { qrScanner.initiateQrScan() }
                )
            }
        }
    }

    /* ─────────────── Resultado del escáner QR ─────────────── */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        qrScanner.handleResult(requestCode, resultCode, data)?.let { contents ->
            try {
                val info = Gson().fromJson(contents, QrInfo::class.java)
                val clientId = "mobile-${UUID.randomUUID()}"
                val svc = Intent(this, com.example.prueba1.ws.WebSocketService::class.java).apply {
                    putExtra("host", BuildConfig.WEBSOCKET_HOST)      // usa 10.0.2.2 en emulador
                    putExtra("clientId",  clientId)
                    putExtra("roomId",    info.room_id)
                    putExtra("password",  info.password)
                }
                startForegroundService(svc)
                Toast.makeText(
                    this,
                    "Conectando a sala...\nRoom ID: ${info.room_id}\nPassword: ${info.password}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "QR inválido\nContenido leído:\n$contents",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
                Log.e("QrScanError", "Error al procesar el QR: ${e.message}")
                Log.e("QrScanError", "Contenido escaneado: $contents", e)
            }
        }
    }
}
