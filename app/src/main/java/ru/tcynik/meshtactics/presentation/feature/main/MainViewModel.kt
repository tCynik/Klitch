package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.launchIn
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
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
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
import ru.tcynik.meshtactics.data.markprefs.GeoMarkPrefsDataSource
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkPreset
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkAddressee
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarksSheetUiState
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteExpiredGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkContextMenuEvent
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MenuDrawerUiState
import java.util.UUID

// BLE RSSI threshold separating low signal (red) from medium/high signal (green).
// Adjust based on field experience; -90 dBm is the standard Meshtastic convention.
private const val RSSI_LOW_THRESHOLD = -90

// Double-tap detection window in milliseconds.
private const val DOUBLE_TAP_WINDOW_MS = 300L

// Proximity threshold for long-tap on a draft point (~30 metres).
// TODO: replace with dp-based calculation using current camera zoom in Phase 3 refinement.
private const val DRAFT_POINT_TOUCH_RADIUS_M = 30.0
private const val METERS_PER_DEG_LAT_APPROX = 111_320.0
private const val GEO_MARK_CLEANUP_INTERVAL_MS = 3_600_000L

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
    observeNodeMarkers: ObserveNodeMarkersUseCase,
    observeConnectionStatus: ObserveConnectionStatusUseCase,
    observeGpsStatus: ObserveGpsStatusUseCase,
    getMarkerSizeLevel: GetMarkerSizeLevelUseCase,
    observeMarkerSizeLevel: ObserveMarkerSizeLevelUseCase,
    observeSelectedOverlays: ObserveSelectedOverlaysUseCase,
    observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
    private val nodeProvisioning: NodeProvisioningUseCase,
    observeGeoMarks: ObserveGeoMarksUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
    ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase,
    private val deleteExpiredGeoMarks: DeleteExpiredGeoMarksUseCase,
    ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase,
    observeLogicalChannels: ObserveContoursUseCase,
    observeNodeChannels: ObserveNodeChannelsUseCase,
    private val checkNodeSync: CheckNodeSyncUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val observeCallsignChanges: ObserveCallsignChangesUseCase,
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase,
    private val geoMarkPrefsDataSource: GeoMarkPrefsDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    private val _formState = MutableStateFlow(GeoMarksFormState())
    private var connectedLabelJob: Job? = null
    private var scanJob: Job? = null
    private var doubleTapJob: Job? = null
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _contextMenuEvent = MutableSharedFlow<GeoMarkContextMenuEvent>()
    val contextMenuEvent: SharedFlow<GeoMarkContextMenuEvent> = _contextMenuEvent.asSharedFlow()

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

        viewModelScope.launch(Dispatchers.Default) {
            deleteExpiredGeoMarks(NoParams)
            while (true) {
                delay(GEO_MARK_CLEANUP_INTERVAL_MS)
                deleteExpiredGeoMarks(NoParams)
            }
        }

        observeLogicalChannels(NoParams)
            .onEach { contours ->
                val addressees = contours
                    .filter { it.isActive }
                    .map { GeoMarkAddressee(it.id.value, it.name) }
                    .toImmutableList()
                _formState.update { form ->
                    val defaultId = if (form.selectedContourId.isEmpty() && addressees.isNotEmpty())
                        addressees.first().contourId else form.selectedContourId
                    form.copy(availableContours = addressees, selectedContourId = defaultId)
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

        geoMarkPrefsDataSource.observePreferences()
            .onEach { prefs -> applyPrefsToFormState(prefs) }
            .launchIn(viewModelScope)

        geoMarkPrefsDataSource.observePresets()
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

    fun toggleMarkTool() {
        _uiState.update { state ->
            if (state.markToolActive) {
                doubleTapJob?.cancel()
                state.copy(markToolActive = false, pendingMarkPoints = persistentListOf())
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

    fun toggleSheetCollapsed() {
        _formState.update { it.copy(isCollapsed = !it.isCollapsed) }
    }

    fun closeGeoMarksSheet() {
        _formState.update { it.copy(isSheetVisible = false) }
        if (_uiState.value.markToolActive) {
            doubleTapJob?.cancel()
            _uiState.update { it.copy(markToolActive = false, pendingMarkPoints = persistentListOf()) }
        }
    }

    fun setMarkType(type: GeoMarkType) {
        _formState.update { it.copy(selectedType = type) }
        viewModelScope.launch { persistFormState() }
    }

    fun setMarkColor(colorIndex: Int) {
        _formState.update { it.copy(selectedColor = colorIndex) }
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
        _formState.update { it.copy(markName = name, nameCounter = 1) }
        viewModelScope.launch { persistFormState() }
    }

    fun setNameCounter(counter: Int) {
        _formState.update { it.copy(nameCounter = counter.coerceAtLeast(1)) }
        viewModelScope.launch { persistFormState() }
    }

    fun setAddressee(contourId: String) {
        _formState.update { it.copy(selectedContourId = contourId) }
        viewModelScope.launch { persistFormState() }
    }

    fun applyPreset(preset: GeoMarkPreset) {
        applyPrefsToFormState(preset.prefs)
        viewModelScope.launch { persistFormState() }
    }

    fun onMapClick(lat: Double, lon: Double) {
        if (!_uiState.value.markToolActive) return
        if (doubleTapJob?.isActive == true) {
            // Second tap within the window — treat as double-tap.
            doubleTapJob?.cancel()
            doubleTapJob = null
            onMapDoubleClick(lat, lon)
            return
        }
        doubleTapJob = viewModelScope.launch {
            delay(DOUBLE_TAP_WINDOW_MS)
            doubleTapJob = null
            val newPoint = GeoPoint(lat, lon)
            _uiState.update { state ->
                val updatedPoints = if (_formState.value.selectedType == GeoMarkType.POINT) {
                    persistentListOf(newPoint)
                } else {
                    (state.pendingMarkPoints + newPoint).toImmutableList()
                }
                state.copy(pendingMarkPoints = updatedPoints)
            }
        }
    }

    fun onMapDoubleClick(lat: Double, lon: Double) {
        if (!_uiState.value.markToolActive) return
        doubleTapJob?.cancel()
        val mark = GeoMarkModel(
            id           = UUID.randomUUID().toString(),
            waypointId   = 0,
            type         = GeoMarkType.POINT,
            points       = listOf(GeoPoint(lat, lon)),
            authorNodeId = "",
            createdAt    = System.currentTimeMillis() / 1_000,
            expiresAt    = null,
            isSelf       = true,
        )
        viewModelScope.launch { sendGeoMark(SendGeoMarkParams(mark)) }
    }

    fun onMapLongClick(lat: Double, lon: Double, screenX: Float, screenY: Float) {
        if (!_uiState.value.markToolActive) return
        val pending = _uiState.value.pendingMarkPoints
        val nearestIndex = pending.indexOfFirst { pt ->
            val dLat = (pt.latitude  - lat) * METERS_PER_DEG_LAT_APPROX
            val dLon = (pt.longitude - lon) * METERS_PER_DEG_LAT_APPROX
            (dLat * dLat + dLon * dLon) < DRAFT_POINT_TOUCH_RADIUS_M * DRAFT_POINT_TOUCH_RADIUS_M
        }
        if (nearestIndex >= 0) {
            viewModelScope.launch {
                _contextMenuEvent.emit(GeoMarkContextMenuEvent(nearestIndex, screenX, screenY))
            }
        }
    }

    fun sendPendingMark() {
        val points = _uiState.value.pendingMarkPoints.toList()
        if (points.isEmpty()) return
        val form = _formState.value
        val nowSeconds = System.currentTimeMillis() / 1_000
        val markLabel = buildMarkLabel(form)
        val contourId = form.selectedContourId.takeIf { it.isNotEmpty() }?.let { ContourId(it) }
        val mark = GeoMarkModel(
            id           = UUID.randomUUID().toString(),
            waypointId   = 0,
            type         = form.selectedType,
            points       = points,
            authorNodeId = "",
            createdAt    = nowSeconds,
            expiresAt    = nowSeconds + form.selectedTtlSeconds,
            isSelf       = true,
            color        = form.selectedColor,
            name         = markLabel,
            trackEndType = form.selectedTrackEndType,
        )
        val updatedCounter = form.nameCounter + 1
        _formState.update { it.copy(nameCounter = updatedCounter) }
        _uiState.update { it.copy(pendingMarkPoints = persistentListOf()) }
        viewModelScope.launch {
            sendGeoMark(SendGeoMarkParams(mark, contourId))
            val updatedForm = _formState.value
            persistFormState()
            savePreset(updatedForm, markLabel)
        }
    }

    fun deletePendingPoint(index: Int) {
        _uiState.update { state ->
            val updated = state.pendingMarkPoints.toMutableList().also { it.removeAt(index) }
            state.copy(pendingMarkPoints = updated.toImmutableList())
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
        compass  = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_compass,    label = "направление", onClick = {}), info = emptyInfoSlot()),
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
                iconRes = R.drawable.ic_radio,
                label = "радио",
                onClick = { nav.onRadioClick(); toggleMenuDrawer() },
            ),
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
        ),
        onDismiss = ::toggleMenuDrawer,
    )

    // Left column — map tools.
    // Row 5 (bottom): GPS signal indicator — ic_satellite tinted by signal level.
    // onClick stubs: each action will be wired when its feature is implemented.
    private fun buildLeftColumn(state: MainUiState) = HudColumnConfig(
        rows = listOf(
            // TODO: wire to compass/bearing mode toggle when implemented
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_compass,   label = "направление",  onClick = {}),
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
                HudInfoSlot(content = "выбор узла", color = Color.Yellow)
            else
                HudInfoSlot(content = "Поиск...", color = Color.Red)
        is MeshConnectionStatus.Connecting ->
            HudInfoSlot(content = "Сопряжение с ${status.deviceName}", color = Color.Yellow)
        is MeshConnectionStatus.Connected ->
            if (state.syncRequired)
                HudInfoSlot(content = "требуется синхронизация", color = Color.Red)
            else if (!state.hasChannelOnNode)
                HudInfoSlot(content = "Настройте канал", color = Color.Red)
            else if (state.showConnectionLabel)
                HudInfoSlot(content = "Сопряжено с ${status.shortName}", color = Color.Green)
            else
                emptyInfoSlot()
        else ->
            if (state.isRebooting)
                HudInfoSlot(content = "Перезагрузка...", color = Color.Yellow)
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
            selectedTrackEndType = form.selectedTrackEndType,
            selectedTtlSeconds   = form.selectedTtlSeconds,
            markName             = form.markName,
            nameCounter          = form.nameCounter,
            pendingPoints        = state.pendingMarkPoints,
            availableContours    = form.availableContours,
            selectedContourId    = form.selectedContourId,
            savedPresets         = form.savedPresets,
            onClose              = ::closeGeoMarksSheet,
            onToggleCollapsed    = ::toggleSheetCollapsed,
            onToggleMarkTool     = ::toggleMarkTool,
            onMarkTypeSelected   = ::setMarkType,
            onColorSelected      = ::setMarkColor,
            onTrackEndTypeSelected = ::setTrackEndType,
            onTtlSelected        = ::setTtl,
            onMarkNameChanged    = ::setMarkName,
            onNameCounterChanged = ::setNameCounter,
            onAddresseeSelected  = ::setAddressee,
            onApplyPreset        = ::applyPreset,
            onSendPendingMark    = ::sendPendingMark,
            onDeletePendingPoint = ::deletePendingPoint,
        )

    private fun buildMarkLabel(form: GeoMarksFormState): String {
        val base = form.markName.trim()
        return if (base.isEmpty()) "${form.nameCounter}" else "$base ${form.nameCounter}"
    }

    private fun applyPrefsToFormState(prefs: GeoMarkFormPreferences) {
        _formState.update { form ->
            form.copy(
                selectedType         = runCatching { GeoMarkType.valueOf(prefs.selectedType) }.getOrDefault(GeoMarkType.POINT),
                selectedColor        = prefs.selectedColor,
                selectedTrackEndType = TrackEndType.fromByte(prefs.selectedTrackEndType.toByte()),
                selectedTtlSeconds   = prefs.selectedTtlSeconds,
                markName             = prefs.markName,
                nameCounter          = prefs.nameCounter,
                selectedContourId    = if (form.selectedContourId.isEmpty()) prefs.selectedContourId else form.selectedContourId,
            )
        }
    }

    private suspend fun persistFormState() {
        val form = _formState.value
        geoMarkPrefsDataSource.savePreferences(
            GeoMarkFormPreferences(
                selectedType         = form.selectedType.name,
                selectedColor        = form.selectedColor,
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                markName             = form.markName,
                nameCounter          = form.nameCounter,
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
                selectedTrackEndType = form.selectedTrackEndType.ends.toInt(),
                selectedTtlSeconds   = form.selectedTtlSeconds,
                markName             = form.markName,
                nameCounter          = form.nameCounter,
                selectedContourId    = form.selectedContourId,
            ),
        )
        geoMarkPrefsDataSource.addPreset(preset)
    }
}
