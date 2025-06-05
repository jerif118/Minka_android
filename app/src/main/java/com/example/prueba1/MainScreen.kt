package com.minka.app



import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ArrowBack
// Material 3 explicit imports
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.example.prueba1.ui.theme.Prueba1Theme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.prueba1.ui.theme.Prueba1Theme


sealed class Dest(val route: String, val icon: ImageVector, val label: String) {
    object Notifications: Dest("main", Icons.Default.Payment, "Pagos")
    object Tools: Dest("tools", Icons.Default.Build, "Herramientas")
    object Settings: Dest("config", Icons.Default.Tune, "Configuración")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController, vm: NotificationViewModel,

               onOpenCameraClicked : () -> Unit,
) {
    Prueba1Theme {
        val dest = listOf(Dest.Notifications, Dest.Tools, Dest.Settings)
        val current by navController.currentBackStackEntryAsState()
        val currentRoute = current?.destination?.route
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "MINKA Potencia tu billetera",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                )
            },
            floatingActionButton = {
                if (currentRoute == Dest.Notifications.route) {
                    FloatingActionButton(
                        onClick = onOpenCameraClicked,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Scan QR",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

        ) { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Dest.Notifications.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Dest.Notifications.route) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            NotificationScreen(vm)

                            // Spacer to push content above the floating bar
                            Spacer(modifier = Modifier.weight(1f))

                            // Floating expandable bar at bottom
                            var bottomMenuExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                ElevatedCard(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .height(56.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(28.dp),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = { bottomMenuExpanded = true },
                                        modifier = Modifier
                                            .size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu expandible",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = bottomMenuExpanded,
                                    onDismissRequest = { bottomMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Opción 1") },
                                        onClick = { bottomMenuExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Opción 2") },
                                        onClick = { bottomMenuExpanded = false }
                                    )
                                    // Add more menu items as needed
                                }
                            }
                        }
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

                if (currentRoute != "Resumen de Ingresos" && currentRoute != "manage_apps" && currentRoute != "linked_devices") {
                    ElevatedCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .wrapContentWidth(),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dest.forEach { destItem ->
                                val selected = currentRoute == destItem.route
                                IconButton(
                                    onClick = { navController.navigate(destItem.route) }
                                ) {
                                    Icon(
                                        imageVector = destItem.icon,
                                        contentDescription = destItem.label,
                                        tint = if (selected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun NotificationCard(n: NotificationData) {
    val hora = remember(n.date) {
        DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(n.date).atZone(ZoneId.systemDefault()))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 300)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
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
                Text(
                    n.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "De: ${n.senderName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "S/ %.2f".format(n.amount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    hora,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement  = Arrangement.spacedBy(8.dp)
    ) {
        items(notifs) { n -> NotificationCard(n) }
    }
}
