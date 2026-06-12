package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class StopTrackRecordingUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(name: String? = null, trimToMovement: Boolean = false) =
        repository.stop(name, trimToMovement)
}
