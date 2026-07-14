package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository

class ImportTrackUseCase(
    private val repository: TrackFileRepository,
) {
    suspend operator fun invoke(sourceUri: String): Result<RecordedTrack> =
        repository.import(sourceUri)
}
