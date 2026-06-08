package ru.tcynik.meshtactics.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.track.model.RecordedTrack
import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveRecordedTracksUseCase(
    private val repository: RecordedTrackRepository,
) : FlowUseCase<NoParams, List<RecordedTrack>>() {
    override fun invoke(params: NoParams): Flow<List<RecordedTrack>> =
        repository.observeTracks()
}
