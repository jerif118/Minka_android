package com.minka.app

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke

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
                OutlinedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = "Gestionar aplicaciones",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManageApps)
                            .padding(vertical = 16.dp, horizontal = 12.dp)
                    )
                }
            }

            item {
                OutlinedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(
                                text = "Dispositivos vinculados",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLinkedDevices)
                            .padding(vertical = 16.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }
}