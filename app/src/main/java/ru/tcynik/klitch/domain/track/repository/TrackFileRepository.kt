package ru.tcynik.klitch.domain.track.repository

import ru.tcynik.klitch.domain.track.model.RecordedTrack

interface TrackFileRepository {
    suspend fun export(trackId: String, destinationUri: String): Result<Unit>
    suspend fun import(sourceUri: String): Result<RecordedTrack>
}
