package com.minka.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minka.app.NotificationData
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    notifications       : List<NotificationData>,
    /*onNotificationSettings   : () -> Unit,*/

    onAppSettings            : () -> Unit,
    onManageAppsClicked : () -> Unit,
    onFabClicked        : () -> Unit,
    onOpenCameraClicked : () -> Unit,


) {
    Scaffold(
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(onClick = onOpenCameraClicked) {
                    Icon(Icons.Default.Call, contentDescription="QR")
                }
                FloatingActionButton(onClick = onFabClicked) {
                    Icon(Icons.Default.MoreVert, contentDescription="Más")
                }
                FloatingActionButton(
                    onClick = onManageAppsClicked,
                    content = {
                        Icon(Icons.Default.Settings, contentDescription = "Configurar notificaciones")
                    }
                )
                FloatingActionButton(onClick = onAppSettings) {
                    Icon(Icons.Default.List, contentDescription = "Gestionar aplicaciones")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement= Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notif ->
                    NotificationCard(notif)
                }
            }
        }
    }
}

@Composable
fun NotificationCard(n: NotificationData) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal=8.dp),
        elevation= CardDefaults.cardElevation(4.dp),
        shape= MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(n.appName, style= MaterialTheme.typography.titleMedium)
            Text("De: ${n.senderName}", style= MaterialTheme.typography.bodyMedium)
            Text(
                "S/ %.2f".format(n.amount),
                style= MaterialTheme.typography.bodyLarge,
                color= MaterialTheme.colorScheme.primary
            )
            val hora = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(n.date))
            Text(hora, style= MaterialTheme.typography.bodySmall)
        }
    }
}
