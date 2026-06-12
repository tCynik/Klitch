package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class UpdateTrackRecordingNameUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(name: String) = repository.updateName(name)
}
