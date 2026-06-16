package ru.tcynik.klitch.domain.track.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.track.model.TrackRecordingSettings

interface TrackSettingsRepository {
    fun observeSettings(): Flow<TrackRecordingSettings>
    suspend fun saveSettings(settings: TrackRecordingSettings)
}
