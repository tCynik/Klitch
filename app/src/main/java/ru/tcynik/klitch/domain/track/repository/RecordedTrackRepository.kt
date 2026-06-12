package ru.tcynik.klitch.domain.track.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.model.TrackPoint

interface RecordedTrackRepository {
    fun observeTracks(): Flow<List<RecordedTrack>>
    fun observeAllPoints(): Flow<List<TrackPoint>>
    suspend fun setVisible(id: String, visible: Boolean)
    suspend fun deleteById(id: String)
}
