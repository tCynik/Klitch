package ru.tcynik.klitch.data.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.service.GpsServiceController
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository

class GpsServiceControllerImpl(
    trackRecordingRepository: TrackRecordingRepository,
) : GpsServiceController {

    private val _networkServiceActive = MutableStateFlow(false)

    override val shouldRunService: Flow<Boolean> = combine(
        _networkServiceActive,
        trackRecordingRepository.state.map { it is TrackRecordingState.Recording },
    ) { net, track -> net || track }
        .distinctUntilChanged()

    override fun onNodeConnected() {
        _networkServiceActive.value = true
    }

    override fun onNetworkDisabled() {
        _networkServiceActive.value = false
    }
}
