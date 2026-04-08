package ru.tcynik.meshtactics.domain.marker.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.marker.model.MarkerModel
import ru.tcynik.meshtactics.domain.marker.model.TrackModel

interface MarkerRepository {
    fun observeMarkers(): Flow<List<MarkerModel>>
    fun observeTracks(): Flow<List<TrackModel>>
    suspend fun saveMarker(marker: MarkerModel)
    suspend fun deleteMarker(markerId: String)
    suspend fun saveTrack(track: TrackModel)
    suspend fun deleteTrack(trackId: String)
}
