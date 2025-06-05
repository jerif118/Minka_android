package com.minka.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.example.prueba1.ws.WebSocketService
import com.minka.app.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(onBack: () -> Unit) {
    val ctx      = LocalContext.current
    val scope    = rememberCoroutineScope()
    val devicesK = stringSetPreferencesKey("linked_devices")

    val devices by ctx.dataStore.data.map { it[devicesK] ?: emptySet() }
        .collectAsState(initial = emptySet())
    DisposableEffect(ctx) {
        val action = WebSocketService.ACTION_SESSION_ENDED
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Al recibir sesión terminada, limpia la lista de dispositivos
                scope.launch {
                    ctx.dataStore.edit { prefs ->
                        prefs[devicesK] = emptySet()
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(ctx)
            .registerReceiver(rx, IntentFilter(WebSocketService.ACTION_SESSION_ENDED))
        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(rx)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dispositivos vinculados", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (devices.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay dispositivos vinculados")
            }
        } else {
            LazyColumn(
                Modifier
                    .padding(pad)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.toList()) { dev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Device",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(dev, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    // Desvincular manual
                                    ctx.stopService(Intent(ctx, WebSocketService::class.java))
                                    WorkManager.getInstance(ctx)
                                        .cancelUniqueWork("ws_reconnect")
                                    ctx.getSharedPreferences("ws_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .remove("clientId")
                                        .remove("roomId")
                                        .remove("password")
                                        .apply()
                                    scope.launch {
                                        ctx.dataStore.edit { p ->
                                            p[devicesK] = p[devicesK]?.minus(dev) ?: emptySet()
                                        }
                                    }
                                }
                            ) {
                                Text("Desvincular")
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
            }
        }
    }
}