package com.example.prueba1

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.minka.app.NotificationData
import com.minka.app.NotificationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.rounded.Construction   // modern replacement for Build
import com.minka.app.UiEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable


class NotificationUI {

}

@Composable
fun FilterBar(
    apps: List<Pair<String, String>>,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(MaterialTheme.shapes.large)   // 28‑dp corners (Expressive)
    ) {
        apps.forEach { (appName, packageName) ->
            val icon = runCatching {
                pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()

            if (icon != null) {
                NavigationBarItem(
                    icon = {
                        Image(
                            bitmap = icon,
                            contentDescription = appName,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    selected = false,
                    onClick = { onFilterSelected(packageName) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun NotificationCard(n: NotificationData, vm: NotificationViewModel) {
    var isEditing by remember { mutableStateOf(false) }
    val MAX_NOTE_LEN = 250
    var editedMessage by rememberSaveable(n.id) { mutableStateOf(n.message ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmEmptyNoteDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    val blinkColor = MaterialTheme.colorScheme.primary
    val blinkProgress = remember { Animatable(0f) }
    val animatedBorderColor = lerp(start = Color.Transparent, stop = blinkColor, fraction = blinkProgress.value)

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

    val isNew = remember(n.date) {
        (System.currentTimeMillis() - n.date) < (15 * 60 * 1000L)
    }

    // --- INICIO DE LA MODIFICACIÓN ---
    // NUEVO: Este LaunchedEffect se encarga del parpadeo de 2 segundos para las NUEVAS notificaciones.
    LaunchedEffect(key1 = n.id) { // La clave es el ID para que se ejecute solo una vez por notificación
        if (isNew) {
            // Repetimos el parpadeo 2 veces para que dure aproximadamente 2 segundos.
            repeat(2) {
                // El borde aparece
                blinkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500) // 0.5 segundos
                )
                // El borde desaparece
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

    Surface(
        shape = MaterialTheme.shapes.large,
        color = backgroundColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
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
                        imageVector = Icons.Rounded.Construction,
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

                    IconButton(
                        onClick = {
                            if (isEditing) {
                                if (editedMessage.length > MAX_NOTE_LEN) {
                                    // Do nothing or show a Toast/snackbar in future
                                } else if (editedMessage.trim().isEmpty()) {
                                    showConfirmEmptyNoteDialog = true
                                } else {
                                    vm.updateNotificationMessage(n.id, editedMessage.trim())
                                    isEditing = false
                                }
                            } else {
                                editedMessage = n.message ?: ""
                                isEditing = true
                            }
                        },
                        enabled = !(isEditing && editedMessage.length > MAX_NOTE_LEN),
                    ) {
                        Icon(
                            imageVector = when {
                                isEditing -> Icons.Rounded.Check
                                n.message?.isNotBlank() == true -> Icons.Rounded.Note
                                else -> Icons.Rounded.Edit
                            },
                            contentDescription = if (isEditing) "Guardar nota" else "Editar nota",
                            tint = primaryTextColor
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

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
                    Text(
                        "S/ %.2f".format(n.amount),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryTextColor
                    )
                    Text(
                        "HORA: $hora",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        color = secondaryTextColor
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (isEditing) {
                    TextField(
                        value = editedMessage,
                        onValueChange = { editedMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Escribe tu nota") },
                        maxLines = 4,
                        supportingText = {
                            Text("${editedMessage.length} / $MAX_NOTE_LEN")
                        },
                        isError = editedMessage.length > MAX_NOTE_LEN,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorContainerColor     = MaterialTheme.colorScheme.errorContainer,
                            cursorColor             = MaterialTheme.colorScheme.primary,
                            focusedLabelColor       = MaterialTheme.colorScheme.primary,
                            errorLabelColor         = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
                } else if (n.message?.isNotBlank() == true) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(180)
                        ) + expandVertically(
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ),
                        exit = fadeOut(
                            animationSpec = tween(180)
                        ) + shrinkVertically(
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Nota:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = secondaryTextColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = n.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
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
    LaunchedEffect(vm.filteredNotifications.size) {
        if (vm.filteredNotifications.isNotEmpty()) {
            listState.animateScrollToItem(0)          // usa animateScrollToItem(0) si quieres animado
        }
    }

    LazyColumn(
        state = listState, // Se asigna el estado a la lista
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