package ru.tcynik.meshtactics.data.markprefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType

class GeoMarkPrefsDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observePreferences(): Flow<GeoMarkFormPreferences> =
        dataStore.data.map { prefs ->
            GeoMarkFormPreferences(
                selectedType        = prefs[KEY_TYPE] ?: GeoMarkType.POINT.name,
                selectedColor       = prefs[KEY_COLOR] ?: 4,
                selectedTrackEndType = prefs[KEY_TRACK_END_TYPE] ?: TrackEndType.NONE.ends.toInt(),
                selectedTtlSeconds  = prefs[KEY_TTL_SECONDS] ?: 28800L,
                markName            = prefs[KEY_MARK_NAME] ?: "",
                nameCounter         = prefs[KEY_NAME_COUNTER] ?: 1,
                selectedContourId   = prefs[KEY_CONTOUR_ID] ?: "",
            )
        }

    suspend fun savePreferences(p: GeoMarkFormPreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_TYPE]             = p.selectedType
            prefs[KEY_COLOR]            = p.selectedColor
            prefs[KEY_TRACK_END_TYPE]   = p.selectedTrackEndType
            prefs[KEY_TTL_SECONDS]      = p.selectedTtlSeconds
            prefs[KEY_MARK_NAME]        = p.markName
            prefs[KEY_NAME_COUNTER]     = p.nameCounter
            prefs[KEY_CONTOUR_ID]       = p.selectedContourId
        }
    }

    fun observePresets(): Flow<List<GeoMarkPreset>> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_PRESETS] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<GeoMarkPreset>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun addPreset(preset: GeoMarkPreset) {
        dataStore.edit { prefs ->
            val existing = run {
                val raw = prefs[KEY_PRESETS] ?: "[]"
                runCatching { json.decodeFromString<List<GeoMarkPreset>>(raw) }.getOrDefault(emptyList())
            }
            val updated = (existing + preset)
                .takeLast(MAX_PRESETS)
            prefs[KEY_PRESETS] = json.encodeToString(updated)
        }
    }

    companion object {
        private const val MAX_PRESETS = 10

        private val KEY_TYPE             = stringPreferencesKey("geomark_type")
        private val KEY_COLOR            = intPreferencesKey("geomark_color")
        private val KEY_TRACK_END_TYPE   = intPreferencesKey("geomark_track_end_type")
        private val KEY_TTL_SECONDS      = longPreferencesKey("geomark_ttl_seconds")
        private val KEY_MARK_NAME        = stringPreferencesKey("geomark_name")
        private val KEY_NAME_COUNTER     = intPreferencesKey("geomark_name_counter")
        private val KEY_CONTOUR_ID       = stringPreferencesKey("geomark_contour_id")
        private val KEY_PRESETS          = stringPreferencesKey("geomark_presets_json")
    }
}
