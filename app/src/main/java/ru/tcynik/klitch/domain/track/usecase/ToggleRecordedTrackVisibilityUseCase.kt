package ru.tcynik.klitch.domain.track.usecase

import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository

class ToggleRecordedTrackVisibilityUseCase(
    private val repository: RecordedTrackRepository,
) {
    suspend operator fun invoke(id: String, visible: Boolean) =
        repository.setVisible(id, visible)
}
