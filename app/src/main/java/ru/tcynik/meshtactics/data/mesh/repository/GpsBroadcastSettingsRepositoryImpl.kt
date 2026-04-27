package ru.tcynik.meshtactics.data.mesh.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.domain.mesh.repository.GpsBroadcastSettingsRepository

class GpsBroadcastSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : GpsBroadcastSettingsRepository {

    override val enabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_GPS_BROADCAST_ENABLED] ?: true }

    override suspend fun set(value: Boolean) {
        dataStore.edit { it[KEY_GPS_BROADCAST_ENABLED] = value }
    }

    companion object {
        val KEY_GPS_BROADCAST_ENABLED = booleanPreferencesKey("gps_broadcast_enabled")
    }
}
