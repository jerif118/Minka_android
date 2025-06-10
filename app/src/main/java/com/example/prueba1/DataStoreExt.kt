package com.minka.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// Aquí solo UNA vez en todo el proyecto
val Context.dataStore by preferencesDataStore(name = "settings")