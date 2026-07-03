package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screenpulse_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val KEY_LAST_UNPLUGGED_TIME = longPreferencesKey("last_unplugged_time")
        private val KEY_LAST_UNPLUGGED_BATTERY = intPreferencesKey("last_unplugged_battery")
        private val KEY_LAST_CHARGE_TIME = longPreferencesKey("last_charge_time")
    }

    val lastUnpluggedTime: Flow<Long> = context.dataStore.data.map { preferences ->
        // Default to a reasonable value (e.g., 4 hours ago) if not set yet
        preferences[KEY_LAST_UNPLUGGED_TIME] ?: (System.currentTimeMillis() - 4 * 3600 * 1000)
    }

    val lastUnpluggedBattery: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_UNPLUGGED_BATTERY] ?: 100
    }

    val lastChargeTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_CHARGE_TIME] ?: (System.currentTimeMillis() - 12 * 3600 * 1000)
    }

    suspend fun saveUnpluggedState(timeMs: Long, batteryLevel: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_UNPLUGGED_TIME] = timeMs
            preferences[KEY_LAST_UNPLUGGED_BATTERY] = batteryLevel
        }
    }

    suspend fun saveLastChargeTime(timeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_CHARGE_TIME] = timeMs
        }
    }
}
