package com.minka.app

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.prueba1.ws.WebSocketManager
import com.example.prueba1.ws.WebSocketService
import com.google.gson.Gson
import java.util.*
import com.example.prueba1.ui.theme.Prueba1Theme

data class QrInfo(val room_id: String, val password: String)

class MainActivity : ComponentActivity() {

    private lateinit var qrScanner: QrScanner
    companion object {
        private const val REQ_DATA_SYNC = 2002   // request‑code para permiso Data‑Sync (API 34+)
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
        // Permiso especial Android 14+ para FGS tipo dataSync
        requestDataSyncPermissionIfNeeded()

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

        // 2) Solicitar exclusión de optimizaciones de batería (opcional)
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intentOpt = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intentOpt)
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
                val nav = rememberNavController()
                val vm: NotificationViewModel = viewModel()

                DisposableEffect(Unit) {
                    MyNotificationListenerService.notificationListener = vm::addNotification
                    onDispose {
                        MyNotificationListenerService.notificationListener = null
                    }
                }
                MainScreen(
                    navController = nav,
                    vm = vm,
                    onOpenCameraClicked = { qrScanner.initiateQrScan() }
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
                    putExtra("host", "192.168.1.11:5001")      // usa 10.0.2.2 en emulador
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
