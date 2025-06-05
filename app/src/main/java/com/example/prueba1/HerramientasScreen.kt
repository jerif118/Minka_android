package com.minka.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerramientasScreen(
    onResumenClicked: () -> Unit,
) {

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Herramientas", color = MaterialTheme.colorScheme.onBackground) }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onResumenClicked
                ) {
                    ListItem(
                        headlineContent = { Text("Resumen de ingresos") },
                        leadingContent  = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }
            }
        }
    )
}