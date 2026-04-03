package ru.tcynik.meshtactics.data.local.marker

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.marker.model.MarkerModel
import ru.tcynik.meshtactics.domain.marker.model.TrackModel
import ru.tcynik.meshtactics.domain.marker.repository.MarkerRepository

class LocalMarkerRepositoryImpl : MarkerRepository {
    override fun observeMarkers(): Flow<List<MarkerModel>> = TODO("SQLDelight implementation pending")
    override fun observeTracks(): Flow<List<TrackModel>> = TODO("SQLDelight implementation pending")
    override suspend fun saveMarker(marker: MarkerModel) = TODO("SQLDelight implementation pending")
    override suspend fun deleteMarker(markerId: String) = TODO("SQLDelight implementation pending")
    override suspend fun saveTrack(track: TrackModel) = TODO("SQLDelight implementation pending")
    override suspend fun deleteTrack(trackId: String) = TODO("SQLDelight implementation pending")
}
