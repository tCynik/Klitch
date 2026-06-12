package ru.tcynik.klitch.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveRecordedTracksUseCase(
    private val repository: RecordedTrackRepository,
) : FlowUseCase<NoParams, List<RecordedTrack>>() {
    override fun invoke(params: NoParams): Flow<List<RecordedTrack>> =
        repository.observeTracks()
}
