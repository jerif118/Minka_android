package com.minka.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minka.app.NotificationData
import com.minka.app.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val NOTIFICATIONS_KEY = "notifications"
    }

    val notifications = mutableStateListOf<NotificationData>()
    private val processedIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            val json = prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] ?: "[]"
            val type = object : TypeToken<List<NotificationData>>() {}.type
            val list: List<NotificationData> = Gson().fromJson(json, type)
            list.forEach { n ->
                if (n.amount > 0 && processedIds.add(n.id)) {
                    notifications.add(n)
                }
            }
        }
    }

    fun addNotification(n: NotificationData) {
        if (n.amount > 0 && processedIds.add(n.id)) {
            notifications.add(0, n)
            viewModelScope.launch {
                val jsonList = Gson().toJson(notifications)
                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[stringPreferencesKey(NOTIFICATIONS_KEY)] = jsonList
                }
            }
        }
    }
}
