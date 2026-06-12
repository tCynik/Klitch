package ru.tcynik.klitch.domain.track.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveTrackRecordingStateUseCase(
    private val repository: TrackRecordingRepository,
) : FlowUseCase<NoParams, TrackRecordingState>() {
    override fun invoke(params: NoParams): Flow<TrackRecordingState> =
        repository.state
}
