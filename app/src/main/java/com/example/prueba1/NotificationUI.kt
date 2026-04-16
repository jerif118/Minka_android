package com.example.prueba1

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.util.LruCache
import com.minka.app.NotificationData
import com.minka.app.NotificationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Build
import java.util.*
import com.minka.app.UiEvent


class NotificationUI {

}

// --- Performance: cache de íconos para evitar jank al cargar/convertir drawables ---
private object AppIconCache {
    // ~4MB de cache para bitmaps pequeños de íconos
    private val cacheSize = 4 * 1024 * 1024
    private val lru = object : LruCache<String, android.graphics.Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
    }

    fun get(packageName: String): android.graphics.Bitmap? = lru.get(packageName)
    fun put(packageName: String, bmp: android.graphics.Bitmap) { lru.put(packageName, bmp) }
}

@Composable
private fun rememberAppIconBitmap(packageName: String): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val pm = context.packageManager
    var image by remember(packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Si ya está en cache, úsalo sin trabajo en UI
    AppIconCache.get(packageName)?.let { return it.asImageBitmap() }

    LaunchedEffect(packageName) {
        // Carga y conversión fuera del hilo principal
        val bmp = withContext(Dispatchers.IO) {
            try {
                val d = pm.getApplicationIcon(packageName)
                d.toBitmap(96, 96, null) // tamaño razonable para listas
            } catch (_: Exception) { null }
        }
        bmp?.let {
            AppIconCache.put(packageName, it)
            image = it.asImageBitmap()
        }
    }
    return image
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
    var editedMessage by remember { mutableStateOf(n.message ?: "") }
    var showFullMessage by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmEmptyNoteDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    val blinkColor = MaterialTheme.colorScheme.primary
    val blinkProgress = remember { Animatable(0f) }
    val animatedBorderColor = lerp(start = Color.Transparent, stop = blinkColor, fraction = blinkProgress.value)

    val expandedBorderColor = MaterialTheme.colorScheme.secondary // <-- El rosado fuerte

    val finalBorderColor = if (showFullMessage) {
        // Si la nota está expandida, usamos el color rosado
        expandedBorderColor
    } else {
        // Si no, usamos el color de la animación de parpadeo que ya existía
        animatedBorderColor
    }

    val messageAtLastBlink = remember { mutableStateOf(n.message) }

    LaunchedEffect(n.message) {
        if (messageAtLastBlink.value != n.message) {
            coroutineScope.launch {
                blinkProgress.animateTo(1f, animationSpec = tween(250))
                blinkProgress.animateTo(0f, animationSpec = tween(durationMillis = 1000, delayMillis = 400))
            }
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

    val fecha = remember(n.date) {
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
            .format(Instant.ofEpochMilli(n.date).atZone(ZoneId.systemDefault()))
    }

    val isNew by remember {
        derivedStateOf { (System.currentTimeMillis() - n.date) < (15 * 60 * 1000L) }
    }

    // --- INICIO DE LA MODIFICACIÓN ---
    // NUEVO: Este LaunchedEffect se encarga del parpadeo de 2 segundos para las NUEVAS notificaciones.
    LaunchedEffect(key1 = "${n.id}-${isNew}") {
        if (isNew) {
            repeat(2) {
                blinkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500) // 0.5 segundos
                )
                blinkProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 500) // 0.5 segundos
                )
            }
        }
    }

    val newNotificationBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val noteBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer
    val editingBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val backgroundColor = when {
        isEditing -> editingBackgroundColor
        isNew -> newNotificationBackgroundColor
        n.message?.isNotBlank() == true -> noteBackgroundColor
        else -> defaultBackgroundColor
    }

    val primaryTextColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = animatedBorderColor,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 300)),
            verticalAlignment = Alignment.Top
        ) {
            val iconBitmap = rememberAppIconBitmap(n.packageName)

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
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                if (isNew) {
                    Text(
                        text = "NUEVA",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-8).dp)
                            .background(
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
                                n.message?.isNotBlank() == true -> Icons.Default.Note
                                else -> Icons.Default.Edit
                            },
                            contentDescription = if (isEditing) "Guardar nota" else "Editar nota",
                            tint = primaryTextColor
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "De: ${n.senderName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val montoFmt = remember(n.amount) { "S/ %.2f".format(n.amount) }
                    Text(
                        montoFmt,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryTextColor
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        // --- NUEVO: Fila para el ícono y la fecha ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Fecha",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = fecha,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            "HORA: $hora",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                } else if (n.message?.isNotBlank() == true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFullMessage = !showFullMessage }
                            .padding(vertical = 2.dp)
                    ) {
                        if (showFullMessage) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 2.dp, // Grosor fijo
                                        color = Color(0xFFB81D57),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Nota:",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar nota",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = n.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nota: ",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface // <-- CAMBIO 2
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



@Composable
fun NotificationScreen(vm: NotificationViewModel) {
    val notifs = vm.filteredNotifications

    // Herramientas para controlar la lista y el scroll
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Cuando el set filtrado cambia (por ejemplo, se quita el filtro)
    // y la lista ya no empieza en el índice 0, volvemos al principio.
    LaunchedEffect(notifs) {
        if (listState.firstVisibleItemIndex != 0) {
            listState.scrollToItem(0)
        }
    }

    // Este efecto escucha los eventos del ViewModel para mover la lista
    LaunchedEffect(key1 = true) {
        vm.uiEvent.collect { event ->
            when (event) {
                is UiEvent.NewNotificationArrived -> {
                    // Si el evento es una nueva notificación, haz scroll al principio
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
                // MODIFICACIÓN: Añadimos el 'else' para hacer el 'when' exhaustivo.
                // Si llega cualquier otro tipo de evento, no hacemos nada.
                else -> {
                    // No se necesita ninguna acción para otros eventos.
                }
            }
        }
    }

    LazyColumn(
        state = listState, // Se asigna el estado a la lista
        modifier = Modifier
            .fillMaxSize()
            //.background(MaterialTheme.colorScheme.outlineVariant)
            //.background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = notifs,
            key = { it.id },
            contentType = { "notification" }
        ) { n ->
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
