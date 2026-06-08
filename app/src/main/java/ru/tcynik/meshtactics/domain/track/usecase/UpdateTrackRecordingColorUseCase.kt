package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class UpdateTrackRecordingColorUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(colorIndex: Int) = repository.updateColor(colorIndex)
}
