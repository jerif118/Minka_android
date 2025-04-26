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

data class QrInfo(val room_id: String, val password: String)

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
            Prueba1Theme {
                val navController = rememberNavController()
                val vm: NotificationViewModel = viewModel()

                // Vinculamos el listener del servicio con el ViewModel
                DisposableEffect(Unit) {
                    MyNotificationListenerService.notificationListener = vm::addNotification
                    onDispose {
                        MyNotificationListenerService.notificationListener = null
                        WebSocketManager.disconnect()
                    }
                }

                NavHost(
                    navController   = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            notifications        = vm.notifications,
                            onManageAppsClicked  = {
                                startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            onAppSettings        = { navController.navigate("settings") },
                            onFabClicked         = { navController.navigate("herramientas") },
                            onOpenCameraClicked  = { qrScanner.initiateQrScan() }
                        )
                    }
                    composable("settings") {
                        AppSettingsScreen(onBack = { navController.navigateUp() })
                    }
                    composable("herramientas") {
                        HerramientasScreen(navController)
                    }
                    composable("resumen_de_ingresos") {
                        ResumenDeIngresosScreen(vm)
                    }
                }
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
                WebSocketManager.connect(
                    hostServidor = "192.168.1.38:5001",   // • usa 10.0.2.2 en emulador
                    clientId     = clientId,
                    roomId       = info.room_id,
                    password     = info.password
                )
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
