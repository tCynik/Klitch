package ru.tcynik.meshtactics.domain.track.usecase

import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository

class DeleteRecordedTracksUseCase(
    private val repository: RecordedTrackRepository,
) {
    suspend operator fun invoke(ids: List<String>) {
        ids.forEach { id -> repository.deleteById(id) }
    }
}
