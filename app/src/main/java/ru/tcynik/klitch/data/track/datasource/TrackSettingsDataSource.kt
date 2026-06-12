package ru.tcynik.klitch.data.track.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.track.model.TrackRecordingPreset
import ru.tcynik.klitch.domain.track.model.TrackRecordingSettings

class TrackSettingsDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    fun observeSettings(): Flow<TrackRecordingSettings> =
        dataStore.data.map { prefs ->
            val preset = runCatching {
                TrackRecordingPreset.valueOf(prefs[KEY_PRESET] ?: TrackRecordingPreset.WALKING.name)
            }.getOrDefault(TrackRecordingPreset.WALKING)
            TrackRecordingSettings(
                preset = preset,
                intervalSeconds = decodeInterval(prefs[KEY_INTERVAL]),
                minDistanceMeters = prefs[KEY_MIN_DISTANCE] ?: preset.defaultMinDistanceMeters(),
                name = prefs[KEY_NAME] ?: "Трек",
                nameCounter = decodeNameCounter(prefs[KEY_NAME_COUNTER]),
                color = prefs[KEY_COLOR] ?: 0,
            )
        }

    suspend fun saveSettings(s: TrackRecordingSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_PRESET] = s.preset.name
            prefs[KEY_INTERVAL] = encodeInterval(s.intervalSeconds)
            prefs[KEY_MIN_DISTANCE] = s.minDistanceMeters
            prefs[KEY_NAME] = s.name
            prefs[KEY_NAME_COUNTER] = encodeNameCounter(s.nameCounter)
            prefs[KEY_COLOR] = s.color
        }
    }

    companion object {
        private const val NO_INTERVAL = -1
        private const val NO_NAME_COUNTER = -1

        private val KEY_PRESET       = stringPreferencesKey("track_preset")
        private val KEY_INTERVAL     = intPreferencesKey("track_interval_seconds")
        private val KEY_MIN_DISTANCE = intPreferencesKey("track_min_distance_meters")
        private val KEY_NAME         = stringPreferencesKey("track_name")
        private val KEY_NAME_COUNTER = intPreferencesKey("track_name_counter")
        private val KEY_COLOR        = intPreferencesKey("track_color")

        private fun encodeInterval(v: Int?): Int = v ?: NO_INTERVAL
        private fun decodeInterval(stored: Int?): Int? = when (stored) {
            null, NO_INTERVAL -> null
            else -> stored
        }

        private fun encodeNameCounter(v: Int?): Int = v ?: NO_NAME_COUNTER
        private fun decodeNameCounter(stored: Int?): Int? = when (stored) {
            null -> 1
            NO_NAME_COUNTER -> null
            else -> stored
        }
    }
}
