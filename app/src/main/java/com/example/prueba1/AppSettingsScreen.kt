package com.minka.app

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
//import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minka.app.MyNotificationListenerService
import com.minka.app.dataStore
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(installedApps) { appInfo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(pm.getApplicationLabel(appInfo).toString())
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
