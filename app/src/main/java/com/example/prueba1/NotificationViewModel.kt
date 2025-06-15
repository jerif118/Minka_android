package com.minka.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log // Import para Log.d

import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel

// Importaciones cruciales para el paquete 'com.minka.app'
import com.minka.app.NotificationData
import com.minka.app.dataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class UiEvent {
    object NewNotificationArrived : UiEvent()
}

sealed class DialogState {
    object Hidden : DialogState()
    data class ConfirmDelete(val notificationId: String) : DialogState()
    data class ConfirmEmptyNote(val notificationId: String, val currentText: String) : DialogState()
}

enum class ConnectionStatus {
    INITIAL,
    CONNECTED,
    DISCONNECTED
}

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val NOTIFICATIONS_KEY = "notifications"
        private const val TAG = "NotificationVM"
    }

    val notifications = mutableStateListOf<NotificationData>()
    private val processedIds = mutableSetOf<String>()

    private val _filteredNotifications = mutableStateListOf<NotificationData>()
    val filteredNotifications: List<NotificationData> = _filteredNotifications

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dialogState = MutableStateFlow<DialogState>(DialogState.Hidden)
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting notification load from DataStore...")
                val prefs = getApplication<Application>().dataStore.data.first()
                val json = prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] ?: "[]"
                val type = object : TypeToken<List<NotificationData>>() {}.type
                val list: List<NotificationData> = Gson().fromJson<List<NotificationData>>(json, type)

                list.forEach { n ->
                    if (n.amount > 0 && processedIds.add(n.id)) {
                        notifications.add(n)
                    }
                }

                _filteredNotifications.clear()
                _filteredNotifications.addAll(notifications)
                Log.d(TAG, "Notifications loaded. Total: ${notifications.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading notifications from DataStore: ${e.message}", e)
            } finally {
                _isLoading.value = false
                Log.d(TAG, "Notification load finished. isLoading = false")
            }
        }
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun addNotification(n: NotificationData) {
        if (n.amount > 0 && processedIds.add(n.id)) {
            // 1. Se añade la notificación a la lista maestra (esto es correcto y se mantiene)
            notifications.add(0, n)

            // --- LÓGICA CORREGIDA ---
            // Se reemplaza la línea `applyFilter(...)` con esta nueva lógica:
            val currentFilter = getCurrentFilterPackageName()
            // La nueva notificación solo se añade a la vista actual si:
            // a) No hay ningún filtro activo (currentFilter es null)
            // b) O si la notificación pertenece al filtro que ya está activo
            if (currentFilter == null || n.packageName == currentFilter) {
                _filteredNotifications.add(0, n)
            }
            // --- FIN DE LA LÓGICA CORREGIDA ---

            viewModelScope.launch {
                val jsonList = Gson().toJson(notifications)
                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] = jsonList
                }
                // Se emite el evento para que la UI sepa que debe hacer scroll al principio
                _uiEvent.emit(UiEvent.NewNotificationArrived)
            }
        }
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.INITIAL)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun updateConnectionStatus(newStatus: ConnectionStatus) {
        _connectionStatus.value = newStatus
    }

    fun updateNotificationMessage(id: String, newMessage: String) {
        val originalNotificationIndex = notifications.indexOfFirst { it.id == id }
        if (originalNotificationIndex != -1) {
            // 1. Actualiza la notificación en la lista principal (notifications)
            val updatedNotification = notifications[originalNotificationIndex].copy(message = newMessage)
            notifications[originalNotificationIndex] = updatedNotification

            // 2. Si la notificación actualizada está actualmente en la lista filtrada, actualízala directamente.
            val filteredIndex = _filteredNotifications.indexOfFirst { it.id == id }
            if (filteredIndex != -1) {
                _filteredNotifications[filteredIndex] = updatedNotification
                Log.d(TAG, "Notification message updated for ID: $id in filtered list.")
            } else {
                Log.d(TAG, "Notification ID: $id not found in current filtered list, UI update not needed for filter.")
            }

            // 3. Guarda la lista actualizada en DataStore
            viewModelScope.launch {
                val jsonList = Gson().toJson(notifications)
                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] = jsonList
                }
                Log.d(TAG, "Notifications saved to DataStore after message update.")
            }
        }
    }

    fun clearNotificationNote(id: String) {
        val originalNotificationIndex = notifications.indexOfFirst { it.id == id }
        if (originalNotificationIndex != -1) {
            // 1. Actualiza la notificación en la lista principal: establece el mensaje a null
            val updatedNotification = notifications[originalNotificationIndex].copy(message = null)
            notifications[originalNotificationIndex] = updatedNotification

            // 2. Si la notificación actualizada está actualmente en la lista filtrada, actualízala directamente.
            val filteredIndex = _filteredNotifications.indexOfFirst { it.id == id }
            if (filteredIndex != -1) {
                _filteredNotifications[filteredIndex] = updatedNotification
                Log.d(TAG, "Notification note cleared for ID: $id in filtered list.")
            } else {
                Log.d(TAG, "Notification ID: $id not found in current filtered list, UI update not needed for filter.")
            }

            // 3. Guarda la lista actualizada en DataStore
            viewModelScope.launch {
                val jsonList = Gson().toJson(notifications)
                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] = jsonList
                }
                Log.d(TAG, "Notification note cleared and saved to DataStore for ID: $id.")
            }
        }
    }

    fun applyFilter(packageName: String?) {
        Log.d(TAG, "Applying filter: ${packageName ?: "NONE"}")
        if (packageName == null) {
            _filteredNotifications.clear()
            _filteredNotifications.addAll(notifications)
        } else {
            _filteredNotifications.clear()
            _filteredNotifications.addAll(notifications.filter { it.packageName == packageName })
        }
    }

    private fun getCurrentFilterPackageName(): String? {
        if (_filteredNotifications.size == notifications.size && _filteredNotifications.containsAll(notifications)) {
            return null
        }
        return _filteredNotifications.firstOrNull()?.packageName
    }

}