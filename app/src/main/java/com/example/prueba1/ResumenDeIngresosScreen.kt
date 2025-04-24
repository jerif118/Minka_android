package com.minka.app

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minka.app.NotificationData
import com.minka.app.NotificationCard
import com.minka.app.NotificationViewModel
import com.minka.app.exportToExcel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenDeIngresosScreen(vm: NotificationViewModel) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val notificaciones = vm.notifications
        .filter {
            Instant.ofEpochMilli(it.date)
                .atZone(ZoneId.systemDefault())
                .toLocalDate() == selectedDate
        }
        .sortedByDescending { it.date }

    val total    = notificaciones.sumOf { it.amount }
    val fmtTotal = String.format("%.2f", total)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de Ingresos") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.onBackPressed() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportToExcel(context, notificaciones) }) {
                        Icon(Icons.Default.List, contentDescription = "Exportar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            // ───── DatePicker ─────
            DatePickerDialog("Fecha:", selectedDate) { newDate ->
                selectedDate = newDate
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ───── Listado de notificaciones ─────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notificaciones) { notif ->
                    NotificationCard(notif)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Total Ingresos: S/ $fmtTotal",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

// ───────── Composable DatePickerDialog ─────────
@Composable
private fun DatePickerDialog(
    label: String,
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        time = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    val picker = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { picker.show() }) {
            Text(text = date.toString())
        }
    }
}
