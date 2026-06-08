package ru.tcynik.meshtactics.domain.track.repository

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.track.model.TrackPoint
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingSettings
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState

sealed interface StopResult {
    data object Saved : StopResult
    data object DiscardedNoMovement : StopResult
}

interface TrackRecordingRepository {
    val state: StateFlow<TrackRecordingState>
    suspend fun start(settings: TrackRecordingSettings)
    suspend fun addPoint(point: TrackPoint)
    suspend fun pause()
    suspend fun resume()
    suspend fun updateName(name: String)
    suspend fun updateColor(colorIndex: Int)
    suspend fun stop(name: String? = null, trimToMovement: Boolean = false): StopResult
    suspend fun discard()
}
