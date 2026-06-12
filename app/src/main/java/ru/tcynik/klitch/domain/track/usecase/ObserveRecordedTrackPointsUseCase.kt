package ru.tcynik.klitch.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.track.model.TrackPoint
import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveRecordedTrackPointsUseCase(
    private val repository: RecordedTrackRepository,
) : FlowUseCase<NoParams, List<TrackPoint>>() {
    override fun invoke(params: NoParams): Flow<List<TrackPoint>> =
        repository.observeAllPoints()
}
