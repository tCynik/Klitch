package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository

class ResumeTrackRecordingUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke() = repository.resume()
}
