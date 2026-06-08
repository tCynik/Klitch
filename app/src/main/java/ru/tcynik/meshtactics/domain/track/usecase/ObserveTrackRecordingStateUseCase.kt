package ru.tcynik.meshtactics.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveTrackRecordingStateUseCase(
    private val repository: TrackRecordingRepository,
) : FlowUseCase<NoParams, TrackRecordingState>() {
    override fun invoke(params: NoParams): Flow<TrackRecordingState> =
        repository.state
}
