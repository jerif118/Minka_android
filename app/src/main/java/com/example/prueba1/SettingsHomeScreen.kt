package com.minka.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onManageApps: () -> Unit,
    onLinkedDevices: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configuración", color = MaterialTheme.colorScheme.onBackground) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    headlineContent = {
                        Text(
                            text = "Gestionar aplicaciones",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageApps)
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                )
                Divider()
            }

            item {
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    headlineContent = {
                        Text(
                            text = "Dispositivos vinculados",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLinkedDevices)
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                )
                Divider()
            }
        }
    }
}