package ru.tcynik.meshtactics.domain.track.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.track.model.RecordedTrack
import ru.tcynik.meshtactics.domain.track.model.TrackPoint

interface RecordedTrackRepository {
    fun observeTracks(): Flow<List<RecordedTrack>>
    fun observeAllPoints(): Flow<List<TrackPoint>>
    suspend fun setVisible(id: String, visible: Boolean)
    suspend fun deleteById(id: String)
}
