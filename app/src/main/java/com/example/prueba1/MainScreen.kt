package com.minka.app

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.prueba1.ui.theme.Prueba1Theme

import androidx.compose.material.icons.filled.*
import com.example.prueba1.NotificationScreen

sealed class Dest(val route: String, val icon: ImageVector, val label: String) {
    object Notifications: Dest("main", Icons.Rounded.CreditCard, "Pagos")
    object Tools: Dest("tools", Icons.Rounded.Handyman, "Herramientas")
    object Settings: Dest("config", Icons.Rounded.Settings, "Configuración")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    vm: NotificationViewModel,
    onOpenCameraClicked: () -> Unit
) {
    val destinations = listOf(Dest.Notifications, Dest.Tools, Dest.Settings)
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showFilters = remember { mutableStateOf(false) }
    val selectedPackage = remember { mutableStateOf<String?>(null) }
    val isLoading by vm.isLoading.collectAsState()
    val connectionStatus by vm.connectionStatus.collectAsState()

    Prueba1Theme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Chekealo.ya",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            val (icon, color, description) = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> Triple(
                                    Icons.Filled.CloudQueue,
                                    MaterialTheme.colorScheme.primary,
                                    "Conectado al servidor"
                                )
                                ConnectionStatus.DISCONNECTED -> Triple(
                                    Icons.Filled.CloudOff,
                                    MaterialTheme.colorScheme.error,
                                    "Sin conexión"
                                )
                                else -> Triple(null, Color.Unspecified, "")
                            }

                            if (icon != null) {
                                val context = LocalContext.current
                                IconButton(onClick = {
                                    Toast.makeText(context, description, Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = description,
                                        tint = color,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                            IconButton(onClick = onOpenCameraClicked) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Escanear QR"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigate(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { inner ->
                Box(
                    modifier = Modifier
                        .padding(inner)
                        .fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Dest.Notifications.route
                    ) {
                        // AHORA LLAMA A NotificationScreen DESDE EL OTRO ARCHIVO
                        composable(Dest.Notifications.route) { NotificationScreen(vm) }
                        composable(Dest.Tools.route) { HerramientasScreen { navController.navigate("Resumen de Ingresos") } }
                        composable(Dest.Settings.route) { SettingsHomeScreen(onManageApps = { navController.navigate("manage_apps") }, onLinkedDevices = { navController.navigate("linked_devices") }) }
                        composable("manage_apps") { AppSettingsScreen { navController.navigateUp() } }
                        composable("linked_devices") { LinkedDevicesScreen { navController.navigateUp() } }
                        composable("Resumen de Ingresos") { ResumenDeIngresosScreen(vm) }
                    }
                }
            }
            if (currentRoute == Dest.Notifications.route) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    val apps = remember(vm.notifications.size) {
                        vm.notifications.map { it.appName to it.packageName }.distinct()
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 110.dp)
                    ) {
                        AnimatedVisibility(
                            visible = showFilters.value,
                            enter = expandVertically(
                                animationSpec = tween(300, easing = LinearOutSlowInEasing),
                                expandFrom = Alignment.Bottom
                            ) + fadeIn(animationSpec = tween(200)),
                            exit = shrinkVertically(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Bottom
                            ) + fadeOut(animationSpec = tween(200))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(bottom = 8.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .animateContentSize(
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    val context = LocalContext.current
                                    val pm = context.packageManager
                                    apps.forEach { (appName, packageName) ->
                                        val icon = try {
                                            pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                                        } catch (_: Exception) { null }
                                        if (icon != null) {
                                            // Animar la aparición / desaparición de cada icono
                                            AnimatedVisibility(
                                                visible = selectedPackage.value == null || selectedPackage.value == packageName,
                                                enter = fadeIn(animationSpec = tween(180)) + scaleIn(animationSpec = tween(180)),
                                                exit  = fadeOut(animationSpec = tween(180)) + scaleOut(animationSpec = tween(180))
                                            ) {
                                                Image(
                                                    bitmap = icon,
                                                    contentDescription = appName,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            if (selectedPackage.value == packageName) {
                                                                // Des‑seleccionar → mostrar todos
                                                                selectedPackage.value = null
                                                                vm.applyFilter(null)
                                                            } else {
                                                                // Seleccionar este paquete → aplicar filtro
                                                                selectedPackage.value = packageName
                                                                vm.applyFilter(packageName)
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        FloatingActionButton(
                            onClick = {
                                showFilters.value = !showFilters.value
                                if (!showFilters.value) vm.applyFilter(null)
                                if (!showFilters.value) selectedPackage.value = null
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (showFilters.value) Icons.Default.Clear else Icons.Default.Tune,
                                contentDescription = "Filtro"
                            )
                        }
                    }
                }
            }
        }
    }
}