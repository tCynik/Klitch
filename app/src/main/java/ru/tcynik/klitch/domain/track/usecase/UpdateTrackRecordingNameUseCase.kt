package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository

class UpdateTrackRecordingNameUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(name: String) = repository.updateName(name)
}
