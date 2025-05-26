package com.minka.app

import com.minka.app.SettingsHomeScreen
import com.minka.app.LinkedDevicesScreen

import android.os.Build
import androidx.camera.core.Camera
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.minka.app.toBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.wear.compose.material3.dynamicColorScheme
import com.minka.app.NotificationData
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
//  NO import de androidx.wear.compose.material3.dynamicColorScheme

//icons
import androidx.compose.material.icons.filled.Camera


@Composable
fun Theme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isSystemInDarkTheme())
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        else ->
            if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}

sealed class Dest(val route: String, val icon: ImageVector, val label: String) {
    object Notifications: Dest("main", Icons.Default.List, "Pagos")
    object Tools: Dest("tools",Icons.Default.Edit,"Herramientas")
    object Settings: Dest("config",Icons.Default.Settings,"Configuración")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController, vm: NotificationViewModel,

               onOpenCameraClicked : () -> Unit,
) {

    val dest = listOf(Dest.Notifications, Dest.Tools, Dest.Settings)
    val current by navController.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MINKA Potencia tu billetera") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenCameraClicked) {
                Icon(Icons.Filled.Camera, contentDescription = "Scan QR")
            }
        },
        bottomBar = {
            NavigationBar {
                dest.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { navController.navigate(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }

    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Dest.Notifications.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(Dest.Notifications.route) {
                NotificationScreen(vm)
            }
            composable(Dest.Tools.route) {
                HerramientasScreen(
                    onResumenClicked = { navController.navigate("Resumen de Ingresos") }
                )
            }
            composable(Dest.Settings.route) {
                SettingsHomeScreen(
                    onManageApps = { navController.navigate("manage_apps") },
                    onLinkedDevices = { navController.navigate("linked_devices") }
                )
            }
            composable("manage_apps") {
                AppSettingsScreen(onBack = { navController.navigateUp() })
            }
            composable("linked_devices") {
                LinkedDevicesScreen(onBack = { navController.navigateUp() })
            }
            composable("Resumen de Ingresos") { ResumenDeIngresosScreen(vm) }
        }

    }
}

@Composable
fun NotificationCard(n: NotificationData) {
    val hora = remember(n.date) {
        DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(n.date).atZone(ZoneId.systemDefault()))
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val pm = context.packageManager
        val iconDrawable = try { pm.getApplicationIcon(n.packageName) } catch (_: Exception) { null }
        val iconBitmap = iconDrawable?.toBitmap()?.asImageBitmap()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = n.appName,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column {
                Text(n.appName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("De: ${n.senderName}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("S/ %.2f".format(n.amount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(hora, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun NotificationScreen(vm: NotificationViewModel) {
    val notifs = vm.notifications        //  ← tu lista observable

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentPadding       = PaddingValues(vertical = 8.dp),
        verticalArrangement  = Arrangement.spacedBy(8.dp)
    ) {
        items(notifs) { n -> NotificationCard(n) }
    }
}
