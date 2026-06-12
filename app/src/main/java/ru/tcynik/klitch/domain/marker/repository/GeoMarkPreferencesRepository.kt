package ru.tcynik.klitch.domain.marker.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.klitch.domain.marker.model.GeoMarkPreset

interface GeoMarkPreferencesRepository {
    fun observePreferences(): Flow<GeoMarkFormPreferences>
    fun observePresets(): Flow<List<GeoMarkPreset>>
    suspend fun savePreferences(prefs: GeoMarkFormPreferences)
    suspend fun addPreset(preset: GeoMarkPreset)
}
