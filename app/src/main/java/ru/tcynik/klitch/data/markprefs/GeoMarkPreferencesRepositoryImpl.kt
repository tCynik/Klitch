package ru.tcynik.klitch.data.markprefs

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.klitch.domain.marker.model.GeoMarkPreset
import ru.tcynik.klitch.domain.marker.repository.GeoMarkPreferencesRepository

class GeoMarkPreferencesRepositoryImpl(
    private val dataSource: GeoMarkPrefsDataSource,
) : GeoMarkPreferencesRepository {
    override fun observePreferences(): Flow<GeoMarkFormPreferences> = dataSource.observePreferences()
    override fun observePresets(): Flow<List<GeoMarkPreset>> = dataSource.observePresets()
    override suspend fun savePreferences(prefs: GeoMarkFormPreferences) = dataSource.savePreferences(prefs)
    override suspend fun addPreset(preset: GeoMarkPreset) = dataSource.addPreset(preset)
}
