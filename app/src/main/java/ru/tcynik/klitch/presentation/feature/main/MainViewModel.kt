package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.klitch.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.klitch.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.klitch.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.presentation.feature.main.osd.models.RecordedTrackRenderModel

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
    observeNodeMarkers: ObserveNodeMarkersUseCase,
    observeGpsStatus: ObserveGpsStatusUseCase,
    getMarkerSizeLevel: GetMarkerSizeLevelUseCase,
    observeMarkerSizeLevel: ObserveMarkerSizeLevelUseCase,
    getGeoMarkSizeLevel: GetGeoMarkSizeLevelUseCase,
    observeGeoMarkSizeLevel: ObserveGeoMarkSizeLevelUseCase,
    getShowGeoMarkNames: GetShowGeoMarkNamesUseCase,
    observeShowGeoMarkNames: ObserveShowGeoMarkNamesUseCase,
    observeSelectedOverlays: ObserveSelectedOverlaysUseCase,
    observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase,
    ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase,
    observeRecordedTracks: ObserveRecordedTracksUseCase,
    observeRecordedTrackPoints: ObserveRecordedTrackPointsUseCase,
    private val observeTrackRecordingState: ObserveTrackRecordingStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _resetBearingEvent = MutableSharedFlow<Unit>()
    val resetBearingEvent: SharedFlow<Unit> = _resetBearingEvent.asSharedFlow()

    private val _restoreZoomEvent = MutableSharedFlow<Double>()
    val restoreZoomEvent: SharedFlow<Double> = _restoreZoomEvent.asSharedFlow()

    init {
        _uiState.update { state ->
            state.copy(
                tileUrlTemplate = getTileUrl(),
                initialCameraPosition = getLastPosition() ?: state.initialCameraPosition,
                markerSizeLevel = getMarkerSizeLevel(),
                geoMarkSizeLevel = getGeoMarkSizeLevel(),
                showGeoMarkNames = getShowGeoMarkNames(),
            )
        }

        observeNodeMarkers(NoParams)
            .onEach { markers -> _uiState.update { it.copy(nodeMarkers = markers.toImmutableList()) } }
            .launchIn(viewModelScope)

        observeGpsStatus(NoParams)
            .onEach { gpsStatus -> _uiState.update { it.copy(gpsStatus = gpsStatus) } }
            .launchIn(viewModelScope)

        observeMarkerSizeLevel(NoParams)
            .onEach { level -> _uiState.update { it.copy(markerSizeLevel = level) } }
            .launchIn(viewModelScope)

        observeGeoMarkSizeLevel(NoParams)
            .onEach { level -> _uiState.update { it.copy(geoMarkSizeLevel = level) } }
            .launchIn(viewModelScope)

        observeShowGeoMarkNames(NoParams)
            .onEach { enabled -> _uiState.update { it.copy(showGeoMarkNames = enabled) } }
            .launchIn(viewModelScope)

        observeSelectedOverlays(NoParams)
            .onEach { overlays -> _uiState.update { it.copy(selectedOverlays = overlays.toImmutableList()) } }
            .launchIn(viewModelScope)

        observeTotalUnreadChatCount(NoParams)
            .onEach { total -> _uiState.update { it.copy(unreadChatCount = total.coerceAtMost(99)) } }
            .launchIn(viewModelScope)

        ingestReceivedChatMessages.observe().launchIn(viewModelScope)

        combine(
            observeRecordedTracks(NoParams),
            observeRecordedTrackPoints(NoParams),
            observeTrackRecordingState(NoParams),
        ) { tracks, allPoints, recordingState ->
            val recordingId = (recordingState as? TrackRecordingState.Recording)?.trackId
            val pointsByTrack = allPoints.groupBy { it.trackId }
            val renderModels = tracks
                .filter { it.isVisible }
                .map { track ->
                    RecordedTrackRenderModel(
                        id = track.id,
                        color = track.color,
                        isRecording = track.id == recordingId,
                        points = (pointsByTrack[track.id] ?: emptyList()).map { it.lat to it.lon },
                    )
                }
            _uiState.update { it.copy(recordedTracks = renderModels.toImmutableList()) }
        }.launchIn(viewModelScope)
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
    }

    fun toggleMenuDrawer() {
        _uiState.update { it.copy(menuDrawerOpen = !it.menuDrawerOpen) }
    }

    fun closeMenuDrawer() {
        _uiState.update { it.copy(menuDrawerOpen = false) }
    }

    fun onFollowMeToggle() {
        _uiState.update { it.copy(isFollowMeActive = !it.isFollowMeActive) }
    }

    fun onFollowMeDeactivated() {
        _uiState.update { it.copy(isFollowMeActive = false) }
    }

    fun onCompassTap() {
        if (_uiState.value.isCourseUpActive) {
            _uiState.update { it.copy(isCourseUpActive = false, zoomAtCourseUpActivation = null) }
        }
        _uiState.update { it.copy(isNorthLocked = true) }
        viewModelScope.launch { _resetBearingEvent.emit(Unit) }
    }

    fun onCourseUpToggle(currentZoom: Double) {
        val current = _uiState.value
        if (current.isCourseUpActive) {
            _uiState.update { it.copy(isCourseUpActive = false, zoomAtCourseUpActivation = null) }
        } else {
            _uiState.update { it.copy(isCourseUpActive = true, isNorthLocked = false, zoomAtCourseUpActivation = currentZoom) }
        }
    }

    fun onCourseUpDeactivated() {
        _uiState.update { it.copy(isCourseUpActive = false, zoomAtCourseUpActivation = null) }
    }

    fun onFollowMeRestoreZoom() {
        val zoom = _uiState.value.zoomAtCourseUpActivation ?: return
        viewModelScope.launch { _restoreZoomEvent.emit(zoom) }
    }

    fun onMapBearingChanged(bearing: Double) {
        _uiState.update { it.copy(mapBearing = bearing.toFloat()) }
    }

    fun onMapRotatedByUser(bearing: Double) {
        _uiState.update { it.copy(mapBearing = bearing.toFloat(), isNorthLocked = false) }
    }
}
