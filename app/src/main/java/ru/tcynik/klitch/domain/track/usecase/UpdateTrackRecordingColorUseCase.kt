package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository

class UpdateTrackRecordingColorUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(colorIndex: Int) = repository.updateColor(colorIndex)
}
