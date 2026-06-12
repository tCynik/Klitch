package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class ResumeTrackRecordingUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke() = repository.resume()
}
