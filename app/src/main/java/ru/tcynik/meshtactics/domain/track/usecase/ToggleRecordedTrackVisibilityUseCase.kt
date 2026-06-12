package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository

class ToggleRecordedTrackVisibilityUseCase(
    private val repository: RecordedTrackRepository,
) {
    suspend operator fun invoke(id: String, visible: Boolean) =
        repository.setVisible(id, visible)
}
