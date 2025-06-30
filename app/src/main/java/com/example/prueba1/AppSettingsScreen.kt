package com.minka.app

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color

// acceso al DataStore
//private val Context.dataStore by preferencesDataStore(name = "settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    // Aquí tu lista de paquetes permitidos
    val allowedPackages = listOf(
        "pe.com.interbank.mobilebanking",
        "com.bcp.innovacxion.yapeapp",
        "com.applemoncash",
        //"com.bitel.bipay",
        "pe.indigital.tunki.user",
         "com.pdp.bim"
        /* … resto de paquetes … */
    )

    // Filtramos solo las apps instaladas de esa lista
    val installedApps = pm.getInstalledApplications(0)
        .filter { allowedPackages.contains(it.packageName) }

    // Flow de los seleccionados en DataStore
    val selectedFlow = context.dataStore.data.map { prefs ->
        prefs[stringSetPreferencesKey("selected_apps")] ?: emptySet()
    }
    // Es necesario dar un initial con el tipo concreto
    val selected by selectedFlow.collectAsState(initial = emptySet<String>())

    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gestionar Aplicaciones", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Toggle notification listener
            val pkgManager = context.packageManager
            val component = ComponentName(context, MyNotificationListenerService::class.java)
            var enabled by remember {
                mutableStateOf(
                    Settings.Secure.getString(
                        context.contentResolver,
                        "enabled_notification_listeners"
                    )?.contains(component.flattenToString()) == true
                )
            }
            OutlinedCard(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface     // ligero, sin bloque gris
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,   // solo decorativo, mismo tamaño que switch
                            contentDescription = null,
                            tint = Color.Transparent,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headlineContent = {
                        Text("Escucha activa", style = MaterialTheme.typography.titleMedium)
                    },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                enabled = checked
                                val state = if (checked)
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                else
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                pm.setComponentEnabledSetting(
                                    component,
                                    state,
                                    PackageManager.DONT_KILL_APP
                                )
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(installedApps) { appInfo ->
                    val iconDrawable = pm.getApplicationIcon(appInfo)
                    val iconBitmap = iconDrawable.toBitmap().asImageBitmap()
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = pm.getApplicationLabel(appInfo).toString(),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                pm.getApplicationLabel(appInfo).toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = selected.contains(appInfo.packageName),
                                onCheckedChange = { checked ->
                                    val updated = selected.toMutableSet().apply {
                                        if (checked) add(appInfo.packageName)
                                        else remove(appInfo.packageName)
                                    }
                                    if (checked) MyNotificationListenerService.allowedPackages.add(appInfo.packageName)
                                    else MyNotificationListenerService.allowedPackages.remove(appInfo.packageName)

                                    scope.launch {
                                        context.dataStore.edit { prefs ->
                                            prefs[stringSetPreferencesKey("selected_apps")] = updated
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
