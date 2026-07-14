package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.repository.TrackFileRepository

class ExportTrackUseCase(
    private val repository: TrackFileRepository,
) {
    suspend operator fun invoke(trackId: String, destinationUri: String): Result<Unit> =
        repository.export(trackId, destinationUri)
}
