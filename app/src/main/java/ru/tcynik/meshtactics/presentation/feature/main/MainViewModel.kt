package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.location.model.GpsSignalLevel
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.DrawerMenuItem
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudRowConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudUiState
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyHudColumn
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyHudRowConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyInfoSlot
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.domain.marker.util.GeoTrackDistance
import android.os.SystemClock
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkAddressee
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarksSheetUiState
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkContextMenuEvent
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.DraftPointContextMenuEvent
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.ExistingMarkContextMenuEvent
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarkTitleFormatter
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MenuDrawerUiState
import java.util.UUID
import kotlin.math.cos

// BLE RSSI threshold separating low signal (red) from medium/high signal (green).
// Adjust based on field experience; -90 dBm is the standard Meshtastic convention.
private const val RSSI_LOW_THRESHOLD = -90
private const val LOCAL_STORAGE_ID = GEO_MARK_LOCAL_STORAGE_ID

// Proximity threshold for long-tap on a draft point (~30 metres).
// TODO: replace with dp-based calculation using current camera zoom in Phase 3 refinement.
private const val DRAFT_POINT_TOUCH_RADIUS_M = 30.0
private const val METERS_PER_DEG_LAT_APPROX = 111_320.0

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
    observeNodeMarkers: ObserveNodeMarkersUseCase,
    observeConnectionStatus: ObserveConnectionStatusUseCase,
    observeGpsStatus: ObserveGpsStatusUseCase,
    getMarkerSizeLevel: GetMarkerSizeLevelUseCase,
    observeMarkerSizeLevel: ObserveMarkerSizeLevelUseCase,
    getGeoMarkSizeLevel: GetGeoMarkSizeLevelUseCase,
    observeGeoMarkSizeLevel: ObserveGeoMarkSizeLevelUseCase,
    getShowGeoMarkNames: GetShowGeoMarkNamesUseCase,
    observeShowGeoMarkNames: ObserveShowGeoMarkNamesUseCase,
    observeSelectedOverlays: ObserveSelectedOverlaysUseCase,
    observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
    private val nodeProvisioning: NodeProvisioningUseCase,
    observeGeoMarks: ObserveGeoMarksUseCase,
    private val toggleGeoMarkVisibility: ToggleGeoMarkVisibilityUseCase,
    private val deleteGeoMarks: DeleteGeoMarksUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
    ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase,
    autoExpireGeoMarks: AutoExpireGeoMarksUseCase,
    ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase,
    observeLogicalChannels: ObserveContoursUseCase,
    observeNodeChannels: ObserveNodeChannelsUseCase,
    private val checkNodeSync: CheckNodeSyncUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val observeCallsignChanges: ObserveCallsignChangesUseCase,
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase,
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    private val _formState = MutableStateFlow(GeoMarksFormState())
    private var connectedLabelJob: Job? = null
    private var scanJob: Job? = null
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastOnMapClickUptimeMs = 0L

    private val _contextMenuEvent = MutableSharedFlow<GeoMarkContextMenuEvent>()
    val contextMenuEvent: SharedFlow<GeoMarkContextMenuEvent> = _contextMenuEvent.asSharedFlow()

    private val _resetBearingEvent = MutableSharedFlow<Unit>()
    val resetBearingEvent: SharedFlow<Unit> = _resetBearingEvent.asSharedFlow()

    private val _restoreZoomEvent = MutableSharedFlow<Double>()
    val restoreZoomEvent: SharedFlow<Double> = _restoreZoomEvent.asSharedFlow()

    // Navigation callbacks provided by NavGraph (has navController access).
    // Updated via provideNavCallbacks() before the first frame renders.
    private val _navCallbacks = MutableStateFlow(HudNavCallbacks())

    // HudConfig is derived from uiState + navCallbacks so it always reflects current state.
    // Contains lambdas → lives outside MainUiState.
    val hudConfig: StateFlow<HudConfig> = combine(_uiState, _navCallbacks) { state, nav ->
        buildHudConfig(state, nav)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = buildHudConfig(MainUiState(), HudNavCallbacks()),
    )

    // Portrait HUD state — named buttons replace the generic slot list.
    // Contains lambdas → separate StateFlow, same pattern as hudConfig.
    val hudUiState: StateFlow<HudUiState> = combine(_uiState, _navCallbacks, _formState) { state, nav, form ->
        buildHudUiState(state, nav, form)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = buildHudUiState(MainUiState(), HudNavCallbacks(), GeoMarksFormState()),
    )

    // Geo marks sheet state — contains lambdas → separate StateFlow.
    val geoMarksSheetUiState: StateFlow<GeoMarksSheetUiState> =
        combine(_uiState, _formState) { state, form ->
            buildGeoMarksSheetUiState(state, form)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildGeoMarksSheetUiState(MainUiState(), GeoMarksFormState()),
        )

    // Menu drawer state — contains lambdas → separate StateFlow.
    val menuDrawerUiState: StateFlow<MenuDrawerUiState> = combine(_uiState, _navCallbacks) { state, nav ->
        buildMenuDrawerUiState(state, nav)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = buildMenuDrawerUiState(MainUiState(), HudNavCallbacks()),
    )

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
            .onEach { markers ->
                _uiState.update { it.copy(nodeMarkers = markers.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        rebootStateRepository.isRebooting
            .onEach { rebooting -> _uiState.update { it.copy(isRebooting = rebooting) } }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams)
            .onEach { status ->
                if (status is MeshConnectionStatus.Connected) {
                    scanJob?.cancel()
                    scanJob = null
                    _uiState.update { it.copy(foundDevices = persistentListOf()) }
                    val wasConnected = _uiState.value.connectionStatus is MeshConnectionStatus.Connected
                    _uiState.update { it.copy(connectionStatus = status) }
                    if (!wasConnected) {
                        if (rebootStateRepository.isRebooting.value) rebootStateRepository.setRebooting(false)
                        viewModelScope.launch { nodeProvisioning.provision() }
                        viewModelScope.launch {
                            if (checkNodeSync() is NodeSyncResult.NeedsSync)
                                syncStateRepository.setSyncRequired(true)
                        }
                        _uiState.update { it.copy(showConnectionLabel = true) }
                        connectedLabelJob?.cancel()
                        connectedLabelJob = viewModelScope.launch {
                            delay(2_000)
                            _uiState.update { it.copy(showConnectionLabel = false) }
                        }
                    }
                } else {
                    val wasConnected = _uiState.value.connectionStatus is MeshConnectionStatus.Connected
                    connectedLabelJob?.cancel()
                    connectedLabelJob = null
                    _uiState.update { it.copy(connectionStatus = status, showConnectionLabel = false) }
                    if (wasConnected && _uiState.value.isRebooting) {
                        viewModelScope.launch {
                            delay(3_000)
                            startAutoConnect()
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        observeGpsStatus(NoParams)
            .onEach { gpsStatus ->
                _uiState.update { it.copy(gpsStatus = gpsStatus) }
            }
            .launchIn(viewModelScope)

        observeMarkerSizeLevel(NoParams)
            .onEach { level ->
                _uiState.update { it.copy(markerSizeLevel = level) }
            }
            .launchIn(viewModelScope)

        observeGeoMarkSizeLevel(NoParams)
            .onEach { level ->
                _uiState.update { it.copy(geoMarkSizeLevel = level) }
            }
            .launchIn(viewModelScope)

        observeShowGeoMarkNames(NoParams)
            .onEach { enabled ->
                _uiState.update { it.copy(showGeoMarkNames = enabled) }
            }
            .launchIn(viewModelScope)

        observeSelectedOverlays(NoParams)
            .onEach { overlays ->
                _uiState.update { it.copy(selectedOverlays = overlays.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        observeTotalUnreadChatCount(NoParams)
            .onEach { total ->
                _uiState.update { it.copy(unreadChatCount = total.coerceAtMost(99)) }
            }
            .launchIn(viewModelScope)

        observeGeoMarks(NoParams)
            .onEach { marks ->
                _uiState.update { it.copy(geoMarks = marks.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        ingestReceivedGeoMarks.observe()
            .launchIn(viewModelScope)

        ingestReceivedChatMessages.observe()
            .launchIn(viewModelScope)

        autoExpireGeoMarks.observe().launchIn(viewModelScope)

        combine(
            observeLogicalChannels(NoParams),
            _uiState.map { it.connectionStatus }.distinctUntilChanged(),
        ) { contours, connectionStatus ->
            val isConnected = connectionStatus is MeshConnectionStatus.Connected
            val active = contours.filter { it.isActive }
            val storage = GeoMarkAddressee(LOCAL_STORAGE_ID, "Хранилище")
            val addressees = (active.map { GeoMarkAddressee(it.id.value, it.name) } + listOf(storage))
                .toImmutableList()
            Triple(addressees, isConnected, active)
        }
            .onEach { (addressees, isConnected, active) ->
                _formState.update { form ->
                    val currentId = form.selectedContourId
                    val stillInList = addressees.any { it.contourId == currentId }
                    val explicitChoice = form.wasAddresseeExplicitlySelected
                        || isPersistedGeoMarkAddresseeChoice(currentId)
                    val newId = when {
                        explicitChoice && stillInList -> currentId
                        explicitChoice && active.isEmpty() -> currentId
                        else -> resolveDefaultGeoMarkAddresseeId(active, isConnected, LOCAL_STORAGE_ID)
                    }
                    form.copy(
                        availableContours = addressees,
                        selectedContourId = newId,
                        wasAddresseeExplicitlySelected = explicitChoice && (stillInList || active.isEmpty()),
                    )
                }
            }
            .launchIn(viewModelScope)

        combine(
            observeLogicalChannels(NoParams),
            observeNodeChannels(NoParams),
        ) { contours, nodeSlots ->
            if (contours.isEmpty()) return@combine true
            contours.any { contour ->
                val hash = contour.transport.meshtastic.channelHash
                nodeSlots.any { slot ->
                    slot.index != 0 && slot.isEnabled &&
                        ContourHash.compute(slot.name, slot.psk) == hash
                }
            }
        }
            .onEach { hasChannel -> _uiState.update { it.copy(hasChannelOnNode = hasChannel) } }
            .launchIn(viewModelScope)

        syncStateRepository.syncRequired
            .onEach { required -> _uiState.update { it.copy(syncRequired = required) } }
            .launchIn(viewModelScope)

        observeCallsignChanges(NoParams)
            .onEach { nodeNum ->
                refreshNodePublicKey(nodeNum)
                delay(10_000)
                refreshNodePublicKey(nodeNum)
            }
            .launchIn(viewModelScope)

        geoMarkPrefsRepository.observePreferences()
            .onEach { prefs -> applyPrefsToFormState(prefs) }
            .launchIn(viewModelScope)

        geoMarkPrefsRepository.observePresets()
            .onEach { presets ->
                _formState.update { it.copy(savedPresets = presets.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        startAutoConnect()
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
    }

    // ── Mark tool ─────────────────────────────────────────────────────────────

    fun toggleMenuDrawer() {
        _uiState.update { it.copy(menuDrawerOpen = !it.menuDrawerOpen) }
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

    fun toggleMarkTool() {
        _uiState.update { state ->
            if (state.markToolActive) {
                state.copy(markToolActive = false).withPendingMarkPoints(persistentListOf())
            } else {
                state.copy(markToolActive = true)
            }
        }
    }

    fun toggleGeoMarksSheet() {
        val isOpening = !_formState.value.isSheetVisible
        _formState.update { it.copy(isSheetVisible = isOpening) }
        if (isOpening) {
            _uiState.update { it.copy(markToolActive = true) }
        }
    }

    /** Вызывается при каждом входе на [Route.Main] (в т.ч. после pop с других экранов). */
    fun onMainDestinationVisible() {
        if (_formState.value.isSheetVisible && !_uiState.value.markToolActive) {
            _uiState.update { it.copy(markToolActive = true) }
        }
    }

    fun toggleSheetCollapsed() {
        _formState.update { it.copy(isCollapsed = !it.isCollapsed) }
    }

    fun closeGeoMarksSheet() {
        _formState.update { it.copy(isSheetVisible = false) }
        if (_uiState.value.markToolActive) {
            _uiState.update { it.copy(markToolActive = false).withPendingMarkPoints(persistentListOf()) }
        }
    }

    fun setMarkType(type: GeoMarkType) {
        val previousType = _formState.value.selectedType
        _formState.update { it.copy(selectedType = type) }
        if (type == GeoMarkType.POINT && previousType == GeoMarkType.TRACK) {
            _uiState.update { state ->
                val pending = state.pendingMarkPoints
                if (pending.size > 1) {
                    state.withPendingMarkPoints(persistentListOf(pending.last()))
                } else {
                    state
                }
            }
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkColor(colorIndex: Int) {
        _formState.update { it.copy(selectedColor = colorIndex) }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkShape(shape: GeoMarkShape) {
        _formState.update { it.copy(selectedShape = shape) }
        viewModelScope.launch { persistFormState() }
    }

    fun setTrackEndType(endType: TrackEndType) {
        _formState.update { it.copy(selectedTrackEndType = endType) }
        viewModelScope.launch { persistFormState() }
    }

    fun setTtl(ttlSeconds: Long) {
        _formState.update { it.copy(selectedTtlSeconds = ttlSeconds) }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkName(name: String) {
        _formState.update { form ->
            if (form.selectedType == GeoMarkType.POINT)
                form.copy(pointMarkName = name, pointNameCounter = 1)
            else
                form.copy(trackMarkName = name, trackNameCounter = 1)
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setNameCounter(counter: Int?) {
        val v = counter?.coerceAtLeast(1)
        _formState.update { form ->
            if (form.selectedType == GeoMarkType.POINT)
                form.copy(pointNameCounter = v)
            else
                form.copy(trackNameCounter = v)
        }
        viewModelScope.launch { persistFormState() }
    }

    fun setAddressee(contourId: String) {
        _formState.update { it.copy(selectedContourId = contourId, wasAddresseeExplicitlySelected = true) }
        viewModelScope.launch { persistFormState() }
    }

    fun applyPreset(preset: GeoMarkPreset) {
        applyPrefsToFormState(preset.prefs)
        viewModelScope.launch { persistFormState() }
    }

    fun onMapClick(lat: Double, lon: Double, screenX: Float, screenY: Float) {
        findNearestVisibleMarkId(lat, lon)?.let { markId ->
            val mark = _uiState.value.geoMarks.firstOrNull { it.id == markId } ?: return@let
            val nodeNames = _uiState.value.nodeMarkers.associate { it.nodeId to it.longName }
            _uiState.update { it.copy(selectedGeoMarkId = markId) }
            viewModelScope.launch {
                _contextMenuEvent.emit(
                    ExistingMarkContextMenuEvent(
                        markId = markId,
                        title = GeoMarkTitleFormatter.selectionTitle(mark, nodeNames),
                        screenX = screenX,
                        screenY = screenY,
                    ),
                )
            }
            return
        }

        _uiState.update { it.copy(selectedGeoMarkId = null) }

        if (!_uiState.value.markToolActive) return
        val now = SystemClock.uptimeMillis()
        if (now - lastOnMapClickUptimeMs < 80L) return
        lastOnMapClickUptimeMs = now
        val markType = _formState.value.selectedType
        val newPoint = GeoPoint(lat, lon)
        _uiState.update { state ->
            val updatedPoints = when (markType) {
                GeoMarkType.TRACK ->
                    (state.pendingMarkPoints + newPoint).toImmutableList()
                GeoMarkType.POINT ->
                    persistentListOf(newPoint)
            }
            state.withPendingMarkPoints(updatedPoints)
        }
    }

    fun onMapDoubleClick(lat: Double, lon: Double) {
        if (!_uiState.value.markToolActive) return
        val markType = _formState.value.selectedType
        when (markType) {
            GeoMarkType.POINT -> {
                _uiState.update { it.withPendingMarkPoints(persistentListOf()) }
                viewModelScope.launch {
                    sendGeoMarkAtPoints(listOf(GeoPoint(lat, lon)), GeoMarkType.POINT)
                }
            }
            GeoMarkType.TRACK -> {
                val newPoint = GeoPoint(lat, lon)
                _uiState.update { state ->
                    state.withPendingMarkPoints((state.pendingMarkPoints + newPoint).toImmutableList())
                }
                sendPendingMark()
            }
        }
    }

    fun onMapLongClick(lat: Double, lon: Double, screenX: Float, screenY: Float) {
        val state = _uiState.value
        if (state.markToolActive) {
            val pending = state.pendingMarkPoints
            val radiusSq = DRAFT_POINT_TOUCH_RADIUS_M * DRAFT_POINT_TOUCH_RADIUS_M
            val nearestIndex = pending.indexOfFirst { pt -> distanceSqMeters(pt, lat, lon) < radiusSq }
            if (nearestIndex >= 0) {
                viewModelScope.launch {
                    _contextMenuEvent.emit(DraftPointContextMenuEvent(nearestIndex, screenX, screenY))
                }
                return
            }
        }

    }

    private fun findNearestVisibleMarkId(lat: Double, lon: Double): String? {
        val radiusSq = DRAFT_POINT_TOUCH_RADIUS_M * DRAFT_POINT_TOUCH_RADIUS_M
        return _uiState.value.geoMarks
            .asSequence()
            .filter { it.isVisible }
            .mapNotNull { mark ->
                val nearestDistance = mark.points.minOfOrNull { pt -> distanceSqMeters(pt, lat, lon) }
                    ?: return@mapNotNull null
                mark.id to nearestDistance
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second < radiusSq }
            ?.first
    }

    private fun distanceSqMeters(
        pt: GeoPoint,
        lat: Double,
        lon: Double,
    ): Double {
        val dLat = (pt.latitude - lat) * METERS_PER_DEG_LAT_APPROX
        val dLon = (pt.longitude - lon) * METERS_PER_DEG_LAT_APPROX * cos(Math.toRadians(lat))
        return dLat * dLat + dLon * dLon
    }

    fun clearSelectedGeoMark() {
        _uiState.update { it.copy(selectedGeoMarkId = null) }
    }

    fun hideGeoMark(markId: String) {
        clearSelectedGeoMark()
        viewModelScope.launch {
            toggleGeoMarkVisibility(markId, visible = false)
        }
    }

    fun requestDeleteGeoMark(markId: String) {
        clearSelectedGeoMark()
        _uiState.update { it.copy(deleteConfirmMarkId = markId) }
    }

    fun confirmDeleteGeoMark() {
        val markId = _uiState.value.deleteConfirmMarkId ?: return
        _uiState.update { it.copy(deleteConfirmMarkId = null) }
        viewModelScope.launch { deleteGeoMarks(listOf(markId)) }
    }

    fun dismissDeleteGeoMarkConfirm() {
        _uiState.update { it.copy(deleteConfirmMarkId = null) }
    }

    fun prepareGeoMarkForResend(markId: String) {
        val mark = _uiState.value.geoMarks.firstOrNull { it.id == markId } ?: return
        clearSelectedGeoMark()
        _formState.update { form ->
            if (mark.type == GeoMarkType.POINT) {
                form.copy(
                    isSheetVisible = true,
                    isCollapsed = false,
                    selectedType = mark.type,
                    selectedColor = mark.color,
                    selectedShape = mark.shape,
                    selectedTrackEndType = mark.trackEndType,
                    pointMarkName = mark.name,
                )
            } else {
                form.copy(
                    isSheetVisible = true,
                    isCollapsed = false,
                    selectedType = mark.type,
                    selectedColor = mark.color,
                    selectedShape = mark.shape,
                    selectedTrackEndType = mark.trackEndType,
                    trackMarkName = mark.name,
                )
            }
        }
        _uiState.update { it.copy(markToolActive = true).withPendingMarkPoints(mark.points.toImmutableList()) }
    }

    fun sendPendingMark() {
        val points = _uiState.value.pendingMarkPoints.toList()
        if (points.isEmpty()) return
        if (_formState.value.selectedType == GeoMarkType.TRACK && points.size < 2) return
        val type = _formState.value.selectedType
        _uiState.update { it.withPendingMarkPoints(persistentListOf()) }
        viewModelScope.launch { sendGeoMarkAtPoints(points, type) }
    }

    fun clearPendingPoints() {
        _uiState.update { it.withPendingMarkPoints(persistentListOf()) }
    }

    fun deletePendingPoint(index: Int) {
        _uiState.update { state ->
            val updated = state.pendingMarkPoints.toMutableList().also { it.removeAt(index) }
            state.withPendingMarkPoints(updated.toImmutableList())
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startAutoConnect() {
        val lastDevice = getLastConnectedDevice()
        scanJob?.cancel()

        val currentStatus = _uiState.value.connectionStatus
        if (lastDevice != null &&
            currentStatus !is MeshConnectionStatus.Connecting &&
            currentStatus !is MeshConnectionStatus.Connected
        ) {
            // Direct connect by address — works even when device is bonded but not advertising
            // (connected to Android OS). Scan runs in parallel so user can switch to another
            // device if lastDevice is unavailable.
            viewModelScope.launch {
                connectToDevice(ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name))
            }
        }

        scanJob = scanDevices(NoParams)
            .onEach { devices ->
                val status = _uiState.value.connectionStatus
                if (status is MeshConnectionStatus.Connected) return@onEach
                _uiState.update { current ->
                    val merged = (current.foundDevices + devices)
                        .distinctBy { it.address }
                        .toImmutableList()
                    current.copy(foundDevices = merged)
                }
            }
            .onCompletion { cause ->
                val status = _uiState.value.connectionStatus
                if (cause == null &&
                    status !is MeshConnectionStatus.Connected &&
                    status !is MeshConnectionStatus.Connecting
                ) {
                    startAutoConnect()
                }
            }
            .catch { /* CancellationException — normal job termination, ignored */ }
            .launchIn(viewModelScope)
    }


    // Called by NavGraph once navController is available.
    fun provideNavCallbacks(callbacks: HudNavCallbacks) {
        _navCallbacks.value = callbacks
    }

    private fun buildHudConfig(state: MainUiState, nav: HudNavCallbacks): HudConfig = HudConfig(
        left = buildLeftColumn(state),
        right = buildRightColumn(state, nav),
    )

    private fun buildHudUiState(state: MainUiState, nav: HudNavCallbacks, form: GeoMarksFormState): HudUiState = HudUiState(
        menuDrawer = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_menu, label = "меню", onClick = { toggleMenuDrawer() }), info = emptyInfoSlot()),
        zoomIn   = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_zoom_in,  label = "+", onClick = {}), info = emptyInfoSlot()),
        zoomOut  = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_zoom_out, label = "-", onClick = {}), info = emptyInfoSlot()),
        compass  = HudRowConfig(
            button = buildCompassButton(state),
            info = if (!state.isNorthLocked)
                HudInfoSlot(content = "${state.mapBearing.toInt()}°", color = Color.Red)
            else
                emptyInfoSlot(),
        ),
        target   = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_target, label = "привязка", selected = state.isFollowMeActive, onClick = { onFollowMeToggle() }), info = emptyInfoSlot()),
        markTool = HudRowConfig(
            button = HudButtonSlot(
                iconRes  = R.drawable.ic_marks_tool,
                label    = "метки",
                selected = state.markToolActive,
                onClick  = { toggleMarkTool() },
            ),
            info = emptyInfoSlot(),
        ),
        mapTools = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_map_tools,  label = "инструменты", onClick = {}), info = emptyInfoSlot()),
        gps      = HudRowConfig(
            button = HudButtonSlot(
                iconRes      = R.drawable.ic_satellite,
                label        = "спутники",
                onClick      = {},
                tintOverride = when (state.gpsStatus.signalLevel) {
                    GpsSignalLevel.None   -> Color.Red
                    GpsSignalLevel.Weak   -> Color.Yellow
                    GpsSignalLevel.Strong -> Color.Green
                },
                infoBadge    = state.gpsStatus.accuracyMeters
                    ?.let { it.toInt().coerceAtMost(99).toString() }
                    .takeIf { it != "0" },
            ),
            info = emptyInfoSlot(),
        ),
        radio    = HudRowConfig(
            button = HudButtonSlot(
                iconRes      = R.drawable.ic_radio,
                label        = "радио",
                onClick      = nav.onRadioClick,
                tintOverride = buildNodeStatusColor(state),
                infoBadge    = when (state.connectionStatus) {
                    is MeshConnectionStatus.Connected -> state.nodeMarkers.size.toString().take(2)
                    else -> null
                }.takeIf { it != "0" },
            ),
            info = buildConnectionInfoSlot(state),
        ),
        marks    = HudRowConfig(
            button = HudButtonSlot(
                iconRes   = R.drawable.ic_marks,
                label     = "метки",
                selected  = if (form.isSheetVisible) true else null,
                onClick   = { toggleGeoMarksSheet() },
                infoBadge = state.pendingMarkPoints.size.takeIf { it > 0 }?.toString(),
            ),
            info = emptyInfoSlot(),
        ),
        chat     = HudRowConfig(
            button = HudButtonSlot(
                iconRes   = R.drawable.ic_chat,
                label     = "чаты",
                onClick   = nav.onChatClick,
                infoBadge = state.unreadChatCount.takeIf { it > 0 }?.toString(),
            ),
            info = emptyInfoSlot(),
        ),
    )

    private fun buildMenuDrawerUiState(state: MainUiState, nav: HudNavCallbacks): MenuDrawerUiState = MenuDrawerUiState(
        isOpen = state.menuDrawerOpen,
        items = listOf(
            DrawerMenuItem(
                iconRes = R.drawable.ic_mesh,
                label = "ноды",
                onClick = { nav.onMeshClick(); toggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_settings,
                label = "Главная",
                onClick = { nav.onMainSettingsClick(); toggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_maps,
                label = "Карта",
                onClick = { nav.onMapSettingsClick(); toggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_night,
                label = "Экран",
                onClick = { nav.onDisplaySettingsClick(); toggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_man,
                label = "Пользователь",
                onClick = { nav.onUserSettingsClick(); toggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_marks,
                label = "Метки",
                onClick = { nav.onGeoMarksList(); toggleMenuDrawer() },
            ),
        ),
        onDismiss = ::toggleMenuDrawer,
    )

    private fun buildCompassButton(state: MainUiState): HudButtonSlot {
        val rotated = !state.isNorthLocked
        return HudButtonSlot(
            iconRes = if (rotated) R.drawable.ic_compass_rotated else R.drawable.ic_compass,
            label = "направление",
            preserveIconColors = rotated,
            iconRotationDegrees = if (rotated) -state.mapBearing - 45f else 0f,
            selected = when {
                state.isCourseUpActive -> true
                state.isNorthLocked -> false
                else -> null
            },
            onClick = { onCompassTap() },
            // long-click wired in MainScreen — needs cameraState.position.zoom at press time
            onLongClick = null,
        )
    }

    // Left column — map tools.
    // Row 5 (bottom): GPS signal indicator — ic_satellite tinted by signal level.
    // onClick stubs: each action will be wired when its feature is implemented.
    private fun buildLeftColumn(state: MainUiState) = HudColumnConfig(
        rows = listOf(
            HudRowConfig(
                button = buildCompassButton(state),
                info = emptyInfoSlot(),
            ),
            HudRowConfig(
                button = HudButtonSlot(
                    iconRes  = R.drawable.ic_target,
                    label    = "привязка",
                    selected = state.isFollowMeActive,
                    onClick  = { onFollowMeToggle() },
                ),
                info = emptyInfoSlot(),
            ),
            HudRowConfig(
                button = HudButtonSlot(
                    iconRes  = R.drawable.ic_marks_tool,
                    label    = "метки",
                    selected = state.markToolActive,
                    onClick  = { toggleMarkTool() },
                ),
                info = emptyInfoSlot(),
            ),
            // TODO: wire to map tools panel when implemented
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_map_tools, label = "инструменты",  onClick = {}),
                info = emptyInfoSlot(),
            ),
            HudRowConfig(
                button = HudButtonSlot(
                    iconRes = R.drawable.ic_satellite,
                    label = "спутники",
                    onClick = {},
                    tintOverride = when (state.gpsStatus.signalLevel) {
                        GpsSignalLevel.None   -> Color.Red
                        GpsSignalLevel.Weak   -> Color.Yellow
                        GpsSignalLevel.Strong -> Color.Green
                    },
                    infoBadge = state.gpsStatus.accuracyMeters
                        ?.let { it.toInt().coerceAtMost(99).toString() }
                        .takeIf { it != "0" },
                ),
                info = emptyInfoSlot(),
            ),
        ),
    )

    // Right column — main menu.
    // Info row 0: node status indicator (connection quality + peer count).
    // Button rows 0–4: radio, settings, mesh, markers, chat.
    private fun buildRightColumn(state: MainUiState, nav: HudNavCallbacks, form: GeoMarksFormState = GeoMarksFormState()): HudColumnConfig =
        HudColumnConfig(
            rows = listOf(
                HudRowConfig(
                    button = HudButtonSlot(
                        iconRes = R.drawable.ic_radio,
                        label = "радио",
                        onClick = nav.onRadioClick,
                        tintOverride = buildNodeStatusColor(state),
                        infoBadge = when (state.connectionStatus) {
                            is MeshConnectionStatus.Connected -> state.nodeMarkers.size.toString().take(2)
                            else -> null
                        }.takeIf { it != "0" },
                    ),
                    info = buildConnectionInfoSlot(state),
                ),
                HudRowConfig(
                    button = HudButtonSlot(
                        iconRes   = R.drawable.ic_marks,
                        label     = "метки",
                        selected  = if (form.isSheetVisible) true else null,
                        onClick   = { toggleGeoMarksSheet() },
                        infoBadge = state.pendingMarkPoints.size.takeIf { it > 0 }?.toString(),
                    ),
                    info = emptyInfoSlot(),
                ),
                HudRowConfig(
                    button = HudButtonSlot(
                        iconRes = R.drawable.ic_chat,
                        label = "чаты",
                        onClick = nav.onChatClick,
                        infoBadge = state.unreadChatCount.takeIf { it > 0 }?.toString(),
                    ),
                    info = emptyInfoSlot(),
                ),
                emptyHudRowConfig(),
                emptyHudRowConfig(),
            ),
        )

    private fun buildConnectionInfoSlot(state: MainUiState): HudInfoSlot = when (val status = state.connectionStatus) {
        MeshConnectionStatus.Scanning ->
            if (state.foundDevices.isNotEmpty())
                HudInfoSlot(content = "выбор узла", color = Color.Black)
            else
                HudInfoSlot(content = "Поиск...", color = Color.Red)
        is MeshConnectionStatus.Connecting ->
            HudInfoSlot(content = "Сопряжение с ${status.deviceName}", color = Color.Black)
        is MeshConnectionStatus.Connected ->
            if (state.syncRequired)
                HudInfoSlot(content = "требуется синхронизация", color = Color.Red)
            else if (!state.hasChannelOnNode)
                HudInfoSlot(content = "Настройте канал", color = Color.Red)
            else if (state.showConnectionLabel)
                HudInfoSlot(content = "Сопряжено с ${status.shortName}", color = Color.Black)
            else if (status.batteryLevel in 1..100)
                HudInfoSlot(
                    content = "🔋${status.batteryLevel}%",
                    color = if (status.batteryLevel < 20) Color.Red else Color.Black,
                )
            else
                emptyInfoSlot()
        else ->
            if (state.isRebooting)
                HudInfoSlot(content = "Перезагрузка...", color = Color.Black)
            else
                emptyInfoSlot()
    }

    private fun buildNodeStatusColor(state: MainUiState): Color {
        if (state.isRebooting) return Color.Yellow
        return when (val status = state.connectionStatus) {
            is MeshConnectionStatus.Connected ->
                if (status.rssi < RSSI_LOW_THRESHOLD) Color.Yellow else Color.Green
            MeshConnectionStatus.Disconnected,
            is MeshConnectionStatus.Error,
            MeshConnectionStatus.DeviceSleep -> Color.Red
            MeshConnectionStatus.Scanning ->
                if (state.foundDevices.isNotEmpty()) Color.Yellow else Color.Red
            is MeshConnectionStatus.Connecting -> Color.Yellow
        }
    }

    private fun buildGeoMarksSheetUiState(state: MainUiState, form: GeoMarksFormState): GeoMarksSheetUiState =
        GeoMarksSheetUiState(
            isVisible            = form.isSheetVisible,
            isCollapsed          = form.isCollapsed,
            markToolActive       = state.markToolActive,
            selectedType         = form.selectedType,
            selectedColor        = form.selectedColor,
            selectedShape        = form.selectedShape,
            selectedTrackEndType = form.selectedTrackEndType,
            selectedTtlSeconds   = form.selectedTtlSeconds,
            markName             = if (form.selectedType == GeoMarkType.POINT) form.pointMarkName else form.trackMarkName,
            nameCounter          = if (form.selectedType == GeoMarkType.POINT) form.pointNameCounter else form.trackNameCounter,
            pendingPoints          = state.pendingMarkPoints,
            trackDraftDistanceLabel = state.trackDraftDistanceLabel,
            availableContours    = form.availableContours,
            selectedContourId    = form.selectedContourId,
            savedPresets         = form.savedPresets,
            onClose              = ::closeGeoMarksSheet,
            onToggleCollapsed    = ::toggleSheetCollapsed,
            onToggleMarkTool     = ::toggleMarkTool,
            onMarkTypeSelected   = ::setMarkType,
            onColorSelected      = ::setMarkColor,
            onShapeSelected      = ::setMarkShape,
            onTrackEndTypeSelected = ::setTrackEndType,
            onTtlSelected        = ::setTtl,
            onMarkNameChanged    = ::setMarkName,
            onNameCounterChanged = ::setNameCounter,
            onAddresseeSelected  = ::setAddressee,
            onApplyPreset        = ::applyPreset,
            onSendPendingMark    = ::sendPendingMark,
            onDeletePendingPoint = ::deletePendingPoint,
            onClearPendingPoints = ::clearPendingPoints,
        )

    private suspend fun sendGeoMarkAtPoints(points: List<GeoPoint>, type: GeoMarkType) {
        val form = _formState.value
        val nowSeconds = System.currentTimeMillis() / 1_000
        val markLabel = buildMarkLabel(form, type)
        val localOnly = form.selectedContourId == LOCAL_STORAGE_ID
        val contourId = if (localOnly) null
                        else form.selectedContourId.takeIf { it.isNotEmpty() }?.let { ContourId(it) }
        val markId = UUID.randomUUID().toString()
        val mark = GeoMarkModel(
            id           = markId,
            waypointId   = GeoMarkWaypointAdapter.waypointIdFromMarkId(markId),
            type         = type,
            points       = points,
            authorNodeId = "",
            createdAt    = nowSeconds,
            expiresAt    = nowSeconds + form.selectedTtlSeconds,
            isSelf       = true,
            color        = form.selectedColor,
            name         = markLabel,
            trackEndType = form.selectedTrackEndType,
            shape        = form.selectedShape,
        )
        sendGeoMark(SendGeoMarkParams(mark, contourId, localOnly))
        _formState.update { s ->
            if (type == GeoMarkType.POINT) {
                s.copy(pointNameCounter = s.pointNameCounter?.plus(1))
            } else {
                s.copy(trackNameCounter = s.trackNameCounter?.plus(1))
            }
        }
        persistFormState()
        savePreset(_formState.value, markLabel)
    }

    private fun buildMarkLabel(form: GeoMarksFormState, type: GeoMarkType): String {
        val base = if (type == GeoMarkType.POINT) form.pointMarkName.trim() else form.trackMarkName.trim()
        val counter = if (type == GeoMarkType.POINT) form.pointNameCounter else form.trackNameCounter
        return when {
            counter == null && base.isEmpty() -> ""
            counter == null -> base
            base.isEmpty() -> "$counter"
            else -> "$base $counter"
        }
    }

    private fun applyPrefsToFormState(prefs: GeoMarkFormPreferences) {
        val prefsType = runCatching { GeoMarkType.valueOf(prefs.selectedType) }
            .getOrDefault(GeoMarkType.POINT)
        _formState.update { form ->
            val preserveType = _uiState.value.pendingMarkPoints.isNotEmpty()
            val persistedAddressee = isPersistedGeoMarkAddresseeChoice(prefs.selectedContourId)
            form.copy(
                selectedType         = if (preserveType) form.selectedType else prefsType,
                selectedColor        = prefs.selectedColor,
                selectedShape        = runCatching { GeoMarkShape.valueOf(prefs.selectedShape) }.getOrDefault(GeoMarkShape.CIRCLE),
                selectedTrackEndType = TrackEndType.fromByte(prefs.selectedTrackEndType.toByte()),
                selectedTtlSeconds   = prefs.selectedTtlSeconds,
                pointMarkName        = prefs.pointMarkName,
                trackMarkName        = prefs.trackMarkName,
                pointNameCounter     = prefs.pointNameCounter,
                trackNameCounter     = prefs.trackNameCounter,
                selectedContourId    = when {
                    form.wasAddresseeExplicitlySelected && form.selectedContourId.isNotEmpty() ->
                        form.selectedContourId
                    persistedAddressee -> prefs.selectedContourId
                    form.selectedContourId.isNotEmpty() -> form.selectedContourId
                    else -> ""
                },
                wasAddresseeExplicitlySelected = form.wasAddresseeExplicitlySelected || persistedAddressee,
            )
        }
    }

    private suspend fun persistFormState() {
        val form = _formState.value
        geoMarkPrefsRepository.savePreferences(
            GeoMarkFormPreferences(
                selectedType         = form.selectedType.name,
                selectedColor        = form.selectedColor,
                selectedShape        = form.selectedShape.name,
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                pointMarkName        = form.pointMarkName,
                trackMarkName        = form.trackMarkName,
                pointNameCounter     = form.pointNameCounter,
                trackNameCounter     = form.trackNameCounter,
                selectedContourId    = form.selectedContourId,
            )
        )
    }

    private suspend fun savePreset(form: GeoMarksFormState, markLabel: String) {
        val preset = GeoMarkPreset(
            id          = UUID.randomUUID().toString(),
            displayName = "${form.selectedType.name} $markLabel",
            prefs       = GeoMarkFormPreferences(
                selectedType         = form.selectedType.name,
                selectedColor        = form.selectedColor,
                selectedShape        = form.selectedShape.name,
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                pointMarkName        = form.pointMarkName,
                trackMarkName        = form.trackMarkName,
                selectedContourId    = form.selectedContourId,
            ),
        )
        geoMarkPrefsRepository.addPreset(preset)
    }

    private fun MainUiState.withPendingMarkPoints(points: ImmutableList<GeoPoint>): MainUiState =
        copy(
            pendingMarkPoints = points,
            trackDraftDistanceLabel = GeoTrackDistance.formatKmRatio(
                GeoTrackDistance.lastSegmentMeters(points),
                GeoTrackDistance.totalMeters(points),
            ),
        )
}
