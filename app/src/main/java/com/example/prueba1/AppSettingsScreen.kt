package com.minka.app

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
//import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minka.app.MyNotificationListenerService
import com.minka.app.dataStore
import com.minka.app.toBitmap
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        "com.applemoncash"
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
        topBar = {
            TopAppBar(
                title = { Text("Gestionar Aplicaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notificaciones activas", modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        val state = if (checked)
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        pkgManager.setComponentEnabledSetting(
                            component,
                            state,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(installedApps) { appInfo ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconDrawable = pm.getApplicationIcon(appInfo)
                        val iconBitmap = iconDrawable.toBitmap().asImageBitmap()
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = pm.getApplicationLabel(appInfo).toString(),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(pm.getApplicationLabel(appInfo).toString())
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = selected.contains(appInfo.packageName),
                            onCheckedChange = { checked ->
                                // actualizamos la lista local y el servicio
                                val updated = selected.toMutableSet().apply {
                                    if (checked) add(appInfo.packageName)
                                    else remove(appInfo.packageName)
                                }
                                if (checked)  MyNotificationListenerService.allowedPackages.add(appInfo.packageName)
                                else          MyNotificationListenerService.allowedPackages.remove(appInfo.packageName)

                                // guardamos en DataStore
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
