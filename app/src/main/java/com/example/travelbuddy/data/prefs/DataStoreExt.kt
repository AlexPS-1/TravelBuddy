package com.example.travelbuddy.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val PREFS_NAME = "travelbuddy_prefs"

/**
 * Single app-wide preferences DataStore.
 * Use applicationContext.prefsDataStore to avoid accidental multiple instances.
 */
val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFS_NAME)