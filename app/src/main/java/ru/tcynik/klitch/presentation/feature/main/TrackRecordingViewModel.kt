package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.gps.usecase.ObserveGpsLocationUseCase
import ru.tcynik.klitch.domain.track.repository.TrackSettingsRepository
import ru.tcynik.klitch.domain.track.model.TrackRecordingPreset
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.repository.StopResult
import ru.tcynik.klitch.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.klitch.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingColorUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingNameUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.presentation.feature.main.osd.models.TrackRecordingSheetUiState

class TrackRecordingViewModel(
    private val startTrackRecording: StartTrackRecordingUseCase,
    private val pauseTrackRecording: PauseTrackRecordingUseCase,
    private val resumeTrackRecording: ResumeTrackRecordingUseCase,
    private val stopTrackRecording: StopTrackRecordingUseCase,
    private val discardTrackRecording: DiscardTrackRecordingUseCase,
    private val updateTrackRecordingName: UpdateTrackRecordingNameUseCase,
    private val updateTrackRecordingColor: UpdateTrackRecordingColorUseCase,
    private val trackSettingsRepository: TrackSettingsRepository,
    observeGpsLocation: ObserveGpsLocationUseCase,
    observeTrackRecordingState: ObserveTrackRecordingStateUseCase,
) : ViewModel() {

    private data class StopDialogState(val requestedAt: Long? = null, val trimToMovement: Boolean = false)

    private val _trackFormState = MutableStateFlow(TrackRecordingFormState())
    private val _stopDialogState = MutableStateFlow(StopDialogState())
    private val _pendingExitOnStop = MutableStateFlow(false)

    private val _exitAppEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitAppEvent: SharedFlow<Unit> = _exitAppEvent.asSharedFlow()

    private val _trackNoMovementDiscardedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackNoMovementDiscardedEvent: SharedFlow<Unit> = _trackNoMovementDiscardedEvent.asSharedFlow()

    val trackRecordingSheetUiState: StateFlow<TrackRecordingSheetUiState> = combine(
        _trackFormState,
        observeTrackRecordingState(NoParams),
        flow {
            while (true) {
                emit(System.currentTimeMillis() / 1000L)
                kotlinx.coroutines.delay(1000)
            }
        },
        _stopDialogState,
        observeGpsLocation(NoParams),
    ) { trackForm, trackState, nowSeconds, stopDialog, gpsLocation ->
        val durationSeconds = when {
            trackState !is TrackRecordingState.Recording -> 0L
            trackState.isPaused -> trackState.accumulatedSeconds
            stopDialog.requestedAt != null -> trackState.accumulatedSeconds + (stopDialog.requestedAt - trackState.activeFromSeconds)
            else -> trackState.accumulatedSeconds + (nowSeconds - trackState.activeFromSeconds)
        }
        buildTrackRecordingSheetUiState(trackForm, trackState, durationSeconds, stopDialog, gpsLocation?.speed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildTrackRecordingSheetUiState(TrackRecordingFormState(), TrackRecordingState.Idle, 0L, StopDialogState(), null),
    )

    init {
        trackSettingsRepository.observeSettings()
            .onEach { settings -> _trackFormState.update { it.copy(settings = settings) } }
            .launchIn(viewModelScope)
    }

    fun toggleTrackRecordingSheet() {
        _trackFormState.update { it.copy(isSheetVisible = !it.isSheetVisible) }
    }

    fun closeTrackRecordingSheetVisibility() {
        _trackFormState.update { it.copy(isSheetVisible = false) }
    }

    fun toggleTrackSheetCollapsed() {
        _trackFormState.update { it.copy(isCollapsed = !it.isCollapsed) }
    }

    fun startTrackRecordingAction() {
        viewModelScope.launch {
            startTrackRecording(_trackFormState.value.settings)
            val s = _trackFormState.value.settings
            _trackFormState.update { it.copy(settings = s.copy(nameCounter = s.nameCounter?.plus(1))) }
            persistCurrentSettings()
        }
    }

    fun pauseTrackRecordingAction() {
        viewModelScope.launch { pauseTrackRecording() }
    }

    fun resumeTrackRecordingAction() {
        viewModelScope.launch { resumeTrackRecording() }
    }

    fun stopTrackRecordingAction() {
        _stopDialogState.update { it.copy(requestedAt = System.currentTimeMillis() / 1000L) }
    }

    fun setStopDialogTrimToMovement(checked: Boolean) {
        _stopDialogState.update { it.copy(trimToMovement = checked) }
    }

    fun requestExitIfSafe() {
        if (trackRecordingSheetUiState.value.recordingState is TrackRecordingState.Recording) {
            _pendingExitOnStop.value = true
            stopTrackRecordingAction()
        } else {
            viewModelScope.launch { _exitAppEvent.emit(Unit) }
        }
    }

    fun confirmTrackStopSave(name: String) {
        val (shouldExit, trimToMovement) = teardownStop()
        viewModelScope.launch {
            val result = stopTrackRecording(name, trimToMovement)
            if (result == StopResult.DiscardedNoMovement) _trackNoMovementDiscardedEvent.emit(Unit)
            if (shouldExit) _exitAppEvent.emit(Unit)
        }
    }

    fun confirmTrackStopDiscard() {
        val (shouldExit, _) = teardownStop()
        viewModelScope.launch {
            discardTrackRecording()
            if (shouldExit) _exitAppEvent.emit(Unit)
        }
    }

    private fun teardownStop(): Pair<Boolean, Boolean> {
        val shouldExit = _pendingExitOnStop.value
        val trimToMovement = _stopDialogState.value.trimToMovement
        _stopDialogState.value = StopDialogState()
        _pendingExitOnStop.value = false
        closeTrackRecordingSheetVisibility()
        return shouldExit to trimToMovement
    }

    fun cancelTrackStopDialog() {
        _stopDialogState.value = StopDialogState()
        _pendingExitOnStop.value = false
    }

    fun setTrackPreset(preset: TrackRecordingPreset) {
        updateAndPersist { form ->
            form.copy(
                settings = form.settings.copy(
                    preset = preset,
                    intervalSeconds = preset.defaultIntervalSeconds(),
                    minDistanceMeters = preset.defaultMinDistanceMeters(),
                ),
            )
        }
    }

    fun setTrackInterval(seconds: Int?) {
        updateAndPersist { form -> form.copy(settings = form.settings.copy(intervalSeconds = seconds, preset = TrackRecordingPreset.CUSTOM)) }
    }

    fun setTrackMinDistance(meters: Int) {
        updateAndPersist { form -> form.copy(settings = form.settings.copy(minDistanceMeters = meters, preset = TrackRecordingPreset.CUSTOM)) }
    }

    fun setTrackName(name: String) {
        updateAndPersist { form -> form.copy(settings = form.settings.copy(name = name)) }
    }

    fun setTrackNameCounter(counter: Int?) {
        updateAndPersist { form -> form.copy(settings = form.settings.copy(nameCounter = counter?.coerceAtLeast(1))) }
    }

    fun setTrackColor(colorIndex: Int) {
        updateAndPersist { form -> form.copy(settings = form.settings.copy(color = colorIndex)) }
        viewModelScope.launch { updateTrackRecordingColor(colorIndex) }
    }

    fun setRecordingTrackName(name: String) {
        viewModelScope.launch { updateTrackRecordingName(name) }
    }

    private fun updateAndPersist(transform: (TrackRecordingFormState) -> TrackRecordingFormState) {
        _trackFormState.update(transform)
        persistCurrentSettings()
    }

    private fun persistCurrentSettings() {
        viewModelScope.launch { trackSettingsRepository.saveSettings(_trackFormState.value.settings) }
    }

    private fun buildTrackRecordingSheetUiState(
        form: TrackRecordingFormState,
        trackState: TrackRecordingState,
        durationSeconds: Long,
        stopDialog: StopDialogState,
        speedMps: Float?,
    ): TrackRecordingSheetUiState = TrackRecordingSheetUiState(
        isVisible               = form.isSheetVisible,
        isCollapsed             = form.isCollapsed,
        settings                = form.settings,
        recordingState          = trackState,
        durationSeconds         = durationSeconds,
        speedMps                = speedMps,
        showStopDialog          = stopDialog.requestedAt != null,
        trimToMovement          = stopDialog.trimToMovement,
        onClose                 = ::closeTrackRecordingSheetVisibility,
        onToggleCollapsed       = ::toggleTrackSheetCollapsed,
        onStart                 = ::startTrackRecordingAction,
        onPause                 = ::pauseTrackRecordingAction,
        onResume                = ::resumeTrackRecordingAction,
        onStop                  = ::stopTrackRecordingAction,
        onStopDialogSave        = ::confirmTrackStopSave,
        onStopDialogDiscard     = ::confirmTrackStopDiscard,
        onStopDialogCancel      = ::cancelTrackStopDialog,
        onTrimToMovementChanged = ::setStopDialogTrimToMovement,
        onPresetSelected        = ::setTrackPreset,
        onIntervalSelected      = ::setTrackInterval,
        onMinDistanceSelected   = ::setTrackMinDistance,
        onNameChanged           = ::setTrackName,
        onNameCounterChanged    = ::setTrackNameCounter,
        onColorSelected         = ::setTrackColor,
        onTrackNameChanged      = ::setRecordingTrackName,
    )
}
