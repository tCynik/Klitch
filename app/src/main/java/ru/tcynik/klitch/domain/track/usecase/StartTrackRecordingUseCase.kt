package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.model.TrackRecordingSettings
import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository

class StartTrackRecordingUseCase(
    private val repository: TrackRecordingRepository,
) {
    suspend operator fun invoke(settings: TrackRecordingSettings) =
        repository.start(settings)
}
