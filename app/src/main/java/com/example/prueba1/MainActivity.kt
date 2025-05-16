package com.minka.app

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.prueba1.ui.theme.Prueba1Theme
import com.google.gson.Gson
import java.util.*
import com.minka.app.NotificationViewModel

class MainActivity : ComponentActivity() {

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

        if (SecureStore.loadToken(this) != null) {
            WebSocketManager.connect(
                hostServidor = "192.168.1.14:5001",
                ctx = this          // sin roomId ni pairingToken
            )
        }

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
        /*WebSocketManager.onNotification = { notificationData ->
            // Esto se ejecutará cuando se reciba una notificación desde el servidor
            runOnUiThread {
                findViewById<NotificationViewModel>(R.id.notificationViewModel)?.addNotification(notificationData)
            }
        }*/

        WebSocketManager.onError = { errorMessage ->
            runOnUiThread {
                Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        }


        setContent {
            Theme {
                val nav = rememberNavController()
                val vm: NotificationViewModel = viewModel()

                DisposableEffect(Unit) {
                    MyNotificationListenerService.notificationListener = vm::addNotification
                    onDispose {
                        MyNotificationListenerService.notificationListener = null
                        WebSocketManager.disconnect()
                    }
                }

                MainScreen(
                    navController = nav,
                    vm           = vm ,
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
                // contents is the JWT pairing_token
                val pairingToken = contents
                // decode payload
                val parts = pairingToken.split('.')
                if (parts.size != 3) throw IllegalArgumentException("Token inválido")
                val payloadJson = String(
                    Base64.decode(parts[1], Base64.URL_SAFE),
                    Charsets.UTF_8
                )
                val payloadMap: Map<*, *>? = Gson().fromJson(payloadJson, Map::class.java)
                val roomId = payloadMap?.get("room_id") as? String
                    ?: throw IllegalArgumentException("room_id no encontrado en token")
                val clientId = "mobile-${UUID.randomUUID()}"
                WebSocketManager.connect(
                    hostServidor = "192.168.1.4:5001",
                    ctx          = this,
                    roomId       = roomId,
                    pairingToken = pairingToken
                )
                Toast.makeText(
                    this,
                    "Conectando a sala...\nRoom ID: $roomId",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "QR inválido\nContenido leído:\n$contents",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("QrScanError", "Error al procesar el QR: ${e.message}", e)
            }
        }
    }
}
