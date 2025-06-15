package com.minka.app

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.prueba1.NotificationCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Asumo que estas clases y funciones existen en tu proyecto.
// import com.minka.app.NotificationData
// import com.minka.app.NotificationCard
// import com.minka.app.NotificationViewModel
// Se importa la función del otro archivo
import com.minka.app.exportToExcel


// Enum para manejar el modo de selección de fecha
enum class DateSelectionMode {
    SINGLE, MULTIPLE, ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenDeIngresosScreen(vm: NotificationViewModel) {
    val context = LocalContext.current
    // Scope para lanzar operaciones en segundo plano
    val coroutineScope = rememberCoroutineScope()

    // --- ESTADOS ---
    var selectionMode by remember { mutableStateOf(DateSelectionMode.SINGLE) }
    var selectedSingleDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var selectedDateRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Estado para mostrar/ocultar el indicador de carga
    var isExporting by remember { mutableStateOf(false) }

    // --- LÓGICA DE FILTRADO ---
    val notificaciones = when (selectionMode) {
        DateSelectionMode.SINGLE -> {
            vm.notifications.filter {
                selectedSingleDate?.let { singleDate ->
                    Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() == singleDate
                } ?: false
            }
        }
            DateSelectionMode.MULTIPLE -> {
                vm.notifications.filter {
                    selectedDateRange?.let { range ->
                        val notificationDate = Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                        !notificationDate.isBefore(range.first) && !notificationDate.isAfter(range.second)
                    } ?: false
                }
            }
            DateSelectionMode.ALL -> vm.notifications
        }.sortedByDescending { it.date
    }

    val totalAmount = notificaciones.sumOf { it.amount }
    val formattedTotal = String.format("%.2f", totalAmount)

    // --- UI ---
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Resumen de Ingresos") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.onBackPressed() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // --- SECCIÓN DE EXPORTACIÓN MODIFICADA ---
                    Box {
                        IconButton(
                            onClick = {
                                // Se inicia una corrutina para no bloquear la UI
                                coroutineScope.launch {
                                    isExporting = true
                                    val fileName = generateFileName(
                                        mode = selectionMode,
                                        singleDate = selectedSingleDate,
                                        dateRange = selectedDateRange
                                    )
                                    // Se llama a la función del archivo ExcelExporter.kt
                                    exportToExcel(context, notificaciones, fileName)
                                    isExporting = false
                                }
                            },
                            // El botón se deshabilita mientras se exporta
                            enabled = !isExporting
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Exportar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Se muestra un indicador de progreso si se está exportando
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total:", style = MaterialTheme.typography.titleMedium)
                    Text(text = "S/ $formattedTotal", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val segments = listOf(
                    DateSelectionMode.SINGLE to "Día",
                    DateSelectionMode.MULTIPLE to "Varios",
                    DateSelectionMode.ALL to "Todos"
                )
                val pill = 28.dp

                segments.forEachIndexed { index, (mode, label) ->
                    val first = index == 0
                    val last  = index == segments.lastIndex

                    SegmentedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = when {
                            first && last -> RoundedCornerShape(pill)
                            first        -> RoundedCornerShape(
                                topStart = pill, bottomStart = pill,
                                topEnd = 0.dp, bottomEnd = 0.dp
                            )
                            last         -> RoundedCornerShape(
                                topEnd = pill, bottomEnd = pill,
                                topStart = 0.dp, bottomStart = 0.dp
                            )
                            else         -> RoundedCornerShape(0.dp)
                        },
                        onClick  = { selectionMode = mode },
                        selected = selectionMode == mode,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor   = MaterialTheme.colorScheme.primary,
                            activeContentColor     = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectionMode != DateSelectionMode.ALL) {
                DateDisplayButton(
                    mode = selectionMode,
                    singleDate = selectedSingleDate,
                    dateRange = selectedDateRange
                ) {
                    showDatePicker = true
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (notificaciones.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("No hay ingresos para la selección actual.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(notificaciones) { notif ->
                        NotificationCard(n = notif, vm = vm)
                    }
                }
            }
        }

        if (showDatePicker) {
            when (selectionMode) {
                DateSelectionMode.SINGLE -> {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = selectedSingleDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { newMillis ->
                                    selectedSingleDate = Instant.ofEpochMilli(newMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                                }
                                showDatePicker = false
                            }) { Text("Aceptar") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
                    ) { DatePicker(state = datePickerState) }
                }
                DateSelectionMode.MULTIPLE -> {
                    val dateRangePickerState = rememberDateRangePickerState(
                        initialSelectedStartDateMillis = selectedDateRange?.first?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
                        initialSelectedEndDateMillis = selectedDateRange?.second?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                if (startMillis != null && endMillis != null) {
                                    val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                                    val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                                    selectedDateRange = Pair(startDate, endDate)
                                }
                                showDatePicker = false
                            }) { Text("Aceptar") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
                    ) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                DateSelectionMode.ALL -> { /* No hace nada */ }
            }
        }
    }
}

@Composable
private fun DateDisplayButton(
    mode: DateSelectionMode,
    singleDate: LocalDate?,
    dateRange: Pair<LocalDate, LocalDate>?,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM uuuu")
    val textToShow = when (mode) {
        DateSelectionMode.SINGLE -> singleDate?.format(formatter) ?: "Seleccionar fecha"
        DateSelectionMode.MULTIPLE -> {
            if (dateRange != null) {
                "${dateRange.first.format(formatter)} - ${dateRange.second.format(formatter)}"
            } else {
                "Seleccionar rango"
            }
        }
        else -> ""
    }

    OutlinedButton(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha", modifier = Modifier.padding(end = 8.dp))
        Text(text = textToShow)
    }
}

private fun generateFileName(
    mode: DateSelectionMode,
    singleDate: LocalDate?,
    dateRange: Pair<LocalDate, LocalDate>?
): String {
    val fileDateFormat = DateTimeFormatter.ofPattern("dd_MM_yyyy")
    val fullDateFormat = DateTimeFormatter.ofPattern("dd_MM_yyyy_HH'h'_mm'm'_ss's'")

    val baseName = "ResumenIngresos"
    val extension = ".xlsx"

    return when (mode) {
        DateSelectionMode.SINGLE -> {
            val dateStr = singleDate?.format(fileDateFormat) ?: "fecha_desconocida"
            "${baseName}_${dateStr}${extension}"
        }
        DateSelectionMode.MULTIPLE -> {
            val startDateStr = dateRange?.first?.format(fileDateFormat) ?: "inicio"
            val endDateStr = dateRange?.second?.format(fileDateFormat) ?: "fin"
            "${baseName}_${startDateStr}-${endDateStr}${extension}"
        }
        DateSelectionMode.ALL -> {
            val nowStr = LocalDateTime.now().format(fullDateFormat)
            "${baseName}_Todo_${nowStr}${extension}"
        }
    }
}