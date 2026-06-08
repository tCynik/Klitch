package ru.tcynik.meshtactics.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.track.model.TrackPoint
import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveRecordedTrackPointsUseCase(
    private val repository: RecordedTrackRepository,
) : FlowUseCase<NoParams, List<TrackPoint>>() {
    override fun invoke(params: NoParams): Flow<List<TrackPoint>> =
        repository.observeAllPoints()
}
