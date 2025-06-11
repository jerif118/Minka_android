package com.minka.app

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.prueba1.ui.theme.Prueba1Theme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.launch


import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue

sealed class Dest(val route: String, val icon: ImageVector, val label: String) {
    object Notifications: Dest("main", Icons.Default.Payment, "Pagos")
    object Tools: Dest("tools", Icons.Default.Build, "Herramientas")
    object Settings: Dest("config", Icons.Default.Tune, "Configuración")
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
    val isLoading by vm.isLoading.collectAsState() // Observa el estado de carga

    val connectionStatus by vm.connectionStatus.collectAsState()

    Prueba1Theme { // Asegúrate de que tu tema esté disponible
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            // Usamos una Columna para apilar los textos verticalmente
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Texto principal
                                Text(
                                    text = "Chekealo.ya",
                                    // Un tamaño de letra un poco más pequeño que el original, pero aún prominente
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Subtítulo con el nombre del usuario
                                Text(
                                    text = "User (Daniel sanchez)",
                                    // Un tamaño de letra más pequeño para el subtítulo
                                    style = MaterialTheme.typography.bodySmall,
                                    // Un color ligeramente más atenuado para dar jerarquía
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            val (icon, color, description) = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> Triple(
                                    Icons.Filled.CloudQueue, // Ícono de nube conectada
                                    MaterialTheme.colorScheme.primary, // Color verde/azul de éxito
                                    "Conectado al servidor"
                                )
                                ConnectionStatus.DISCONNECTED -> Triple(
                                    Icons.Filled.CloudOff, // Ícono de nube desconectada
                                    MaterialTheme.colorScheme.error, // Color rojo de error
                                    "Sin conexión"
                                )
                                // 'else' soluciona el error de "when must be exhaustive"
                                // y cubre el estado INITIAL.
                                else -> Triple(null, Color.Unspecified, "")
                            }

                            if (icon != null) {
                                // Necesitamos el contexto para mostrar el Toast
                                val context = LocalContext.current
                                // Envolvemos el Icon en un IconButton para hacerlo clickeable
                                IconButton(onClick = {
                                    // Mostramos un Toast con el estado al hacer clic
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
                            // --- FIN DE LA CORRECCIÓN ---
                            IconButton(onClick = onOpenCameraClicked) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Escanear QR" // Descripción más accesible
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            // El color del TopAppBar será el del fondo para un look integrado
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp // Elevación sutil para dar profundidad
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
                            // Colores que se adaptan al tema claro/oscuro
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
                        composable(Dest.Notifications.route) { NotificationScreen(vm) }
                        composable(Dest.Tools.route) { HerramientasScreen { navController.navigate("Resumen de Ingresos") } }
                        composable(Dest.Settings.route) { SettingsHomeScreen(onManageApps = { navController.navigate("manage_apps") }, onLinkedDevices = { navController.navigate("linked_devices") }) }
                        composable("manage_apps") { AppSettingsScreen { navController.navigateUp() } }
                        composable("linked_devices") { LinkedDevicesScreen { navController.navigateUp() } }
                        composable("Resumen de Ingresos") { ResumenDeIngresosScreen(vm) }
                    }
                }
            }

            // Botones flotantes y filtro
            if (currentRoute == Dest.Notifications.route) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    //val apps = remember(vm.notifications) {
                    //    vm.notifications.map { it.appName to it.packageName }.distinct()
                    //}
                    val apps = remember(vm.notifications.size) {
                        // El bloque interno no cambia
                        vm.notifications.map { it.appName to it.packageName }.distinct()
                    }
                    // Botón de filtro y sus iconos (izquierda)
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
                                // MODIFICADO: Usa un color de contenedor del tema
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(bottom = 8.dp)
                                    .align(Alignment.CenterHorizontally)
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
                                            Image(
                                                bitmap = icon,
                                                contentDescription = appName,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        vm.applyFilter(packageName)
                                                        showFilters.value = false
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Botón FAB para el filtro
                        FloatingActionButton(
                            onClick = {
                                showFilters.value = !showFilters.value
                                if (!showFilters.value) vm.applyFilter(null)
                            },
                            // MODIFICADO: Colores del tema para el FAB
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

@Composable
fun FilterBar(
    apps: List<Pair<String, String>>,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps) { (appName, packageName) ->
                val icon = try {
                    pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onFilterSelected(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationCard(n: NotificationData, vm: NotificationViewModel) {
    var isEditing by remember { mutableStateOf(false) }
    var editedMessage by remember(n.message) { mutableStateOf(n.message ?: "") }
    var showFullMessage by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmEmptyNoteDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    val blinkColor = MaterialTheme.colorScheme.primary
    val blinkProgress = remember { Animatable(0f) }
    val animatedBorderColor = lerp(start = Color.Transparent, stop = blinkColor, fraction = blinkProgress.value)

    val messageAtLastBlink = remember { mutableStateOf(n.message) }

    LaunchedEffect(n.message) {
        // La animación se ejecuta si el mensaje de la notificación es diferente
        // al último mensaje que recordamos haber animado.
        if (messageAtLastBlink.value != n.message) {

            // MODIFICACIÓN CLAVE: Hemos quitado la condición que comprobaba si el mensaje
            // era nulo o no. Ahora, cualquier cambio (agregar, editar, O ELIMINAR)
            // activará la animación.
            coroutineScope.launch {
                blinkProgress.animateTo(1f, animationSpec = tween(250))
                blinkProgress.animateTo(0f, animationSpec = tween(durationMillis = 1000, delayMillis = 400))
            }

            // CRÍTICO: Actualizamos nuestra memoria con el mensaje actual.
            messageAtLastBlink.value = n.message
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    val hora = remember(n.date) {
        DateTimeFormatter.ofPattern("hh:mm a")
            .format(Instant.ofEpochMilli(n.date).atZone(ZoneId.systemDefault()))
    }

    val isNew = remember(n.date) {
        (System.currentTimeMillis() - n.date) < (15 * 60 * 1000L)
    }

    val newNotificationBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val noteBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer
    val editingBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val backgroundColor = when {
        isEditing -> editingBackgroundColor
        isNew -> newNotificationBackgroundColor
        n.message?.isNotBlank() == true -> noteBackgroundColor // Usamos n.message como fuente de verdad
        else -> defaultBackgroundColor
    }

    val primaryTextColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border( // El borde ahora está en el Box exterior
                width = 2.dp,
                color = animatedBorderColor,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(backgroundColor) // El fondo se queda en el Row interior
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 300)),
            verticalAlignment = Alignment.Top
        ) {
            val context = LocalContext.current
            val pm = context.packageManager
            val iconDrawable = try {
                pm.getApplicationIcon(n.packageName)
            } catch (_: Exception) {
                null
            }
            val iconBitmap = iconDrawable?.toBitmap()?.asImageBitmap()

            Box(
                modifier = Modifier.padding(top = 8.dp, end = 12.dp)
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = n.appName,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = n.appName,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline // Un color neutral del tema
                    )
                }

                if (isNew) {
                    Text(
                        text = "NUEVA",
                        // Color de texto que contrasta con el fondo del badge
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-8).dp)
                            .background(
                                // Color de fondo del badge del tema
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        n.appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        // Usa el color correspondiente al fondo de la tarjeta
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(onClick = {
                        if (isEditing) {
                            if (editedMessage.trim().isEmpty()) {
                                showConfirmEmptyNoteDialog = true
                            } else {
                                vm.updateNotificationMessage(n.id, editedMessage.trim())
                                isEditing = false
                            }
                        } else {
                            editedMessage = n.message ?: ""
                            isEditing = true
                        }
                    }) {
                        Icon(
                            imageVector = when {
                                isEditing -> Icons.Default.Check
                                editedMessage.isNotBlank() -> Icons.Default.Note
                                else -> Icons.Default.Edit
                            },
                            contentDescription = if (isEditing) "Guardar nota" else "Editar nota",
                            // Usa un color primario del tema para el icono
                            tint = primaryTextColor
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "De: ${n.senderName}",
                    style = MaterialTheme.typography.bodyMedium,
                    // Un color de texto secundario para menor énfasis
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "S/ %.2f".format(n.amount),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        // Usa el color primario del tema
                        color = primaryTextColor
                    )
                    Text(
                        "HORA: $hora",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        // Usa el color secundario del tema
                        color = secondaryTextColor
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = editedMessage,
                        onValueChange = { editedMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.dp)
                            .focusRequester(focusRequester),
                        label = { Text("Escribe tu nota") },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        // Colores del TextField que se adaptan al tema
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.3f
                            ),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else if (n.message?.isNotBlank() == true) { // Usamos n.message como fuente de verdad
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullMessage = !showFullMessage }
                            .padding(vertical = 2.dp)
                    ) {
                        if (showFullMessage) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Nota:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    // Color de texto secundario del tema
                                    color = secondaryTextColor
                                )
                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar nota",
                                        // Color de error del tema
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = n.message,
                                style = MaterialTheme.typography.bodyMedium,
                                // Color principal sobre la superficie de la nota
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nota: ",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = n.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                onConfirm = {
                    vm.clearNotificationNote(n.id)
                    showFullMessage = false
                    showDeleteDialog = false
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
        if (showConfirmEmptyNoteDialog) {
            ConfirmEmptyNoteDialog(
                onConfirm = {
                    vm.updateNotificationMessage(n.id, editedMessage.trim())
                    isEditing = false
                    showFullMessage = false
                    showConfirmEmptyNoteDialog = false
                },
                onDismiss = { showConfirmEmptyNoteDialog = false }
            )
        }
    }
}

@Composable
fun NotificationScreen(vm: NotificationViewModel) {
    val notifs = vm.filteredNotifications

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(notifs, key = { it.id }) { n ->
            NotificationCard(n, vm)
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar eliminación", style = MaterialTheme.typography.titleLarge) },
        text = { Text("¿Estás seguro de que quieres eliminar esta nota? Esta acción no se puede deshacer.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                // El color de error ya se obtiene del tema
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        // Colores del diálogo que se adaptan al tema
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ConfirmEmptyNoteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nota vacía", style = MaterialTheme.typography.titleLarge) },
        text = { Text("Has dejado la nota vacía. ¿Estás seguro de guardar los cambios? Esto borrará la nota.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                // El color de error ya se obtiene del tema
                Text("Guardar (vacío)", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        // Colores del diálogo que se adaptan al tema
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}