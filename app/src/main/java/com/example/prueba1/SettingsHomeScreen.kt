package com.minka.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onManageApps: () -> Unit,
    onLinkedDevices: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Configuración") })
    }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    leadingContent = { Icon(imageVector = Icons.Default.Apps, contentDescription = "Apps") },
                    headlineContent = { Text("Gestionar aplicaciones") },
                    modifier = Modifier
                        .clickable(onClick = onManageApps)
                        .padding(vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Devices") },
                    headlineContent = { Text("Dispositivos vinculados") },
                    modifier = Modifier
                        .clickable(onClick = onLinkedDevices)
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}