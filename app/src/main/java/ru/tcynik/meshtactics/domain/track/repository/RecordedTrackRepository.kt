package ru.tcynik.meshtactics.domain.track.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.track.model.RecordedTrack

interface RecordedTrackRepository {
    fun observeTracks(): Flow<List<RecordedTrack>>
    suspend fun setVisible(id: String, visible: Boolean)
    suspend fun deleteById(id: String)
}
