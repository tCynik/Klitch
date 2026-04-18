package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudRowConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyHudColumn
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyInfoSlot
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkContextMenuEvent
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
    observeGeoMarks: ObserveGeoMarksUseCase,
    private val sendGeoMark: SendGeoMarkUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
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

        observeConnectionStatus(NoParams)
            .onEach { status ->
                if (status is MeshConnectionStatus.Connecting || status is MeshConnectionStatus.Connected) {
                    scanJob?.cancel()
                    scanJob = null
                    _uiState.update { it.copy(foundDevices = persistentListOf()) }
                }
                if (status is MeshConnectionStatus.Connected) {
                    val wasConnected = _uiState.value.connectionStatus is MeshConnectionStatus.Connected
                    _uiState.update { it.copy(connectionStatus = status) }
                    if (!wasConnected) {
                        _uiState.update { it.copy(showConnectionLabel = true) }
                        connectedLabelJob?.cancel()
                        connectedLabelJob = viewModelScope.launch {
                            delay(2_000)
                            _uiState.update { it.copy(showConnectionLabel = false) }
                        }
                    }
                } else {
                    connectedLabelJob?.cancel()
                    connectedLabelJob = null
                    _uiState.update { it.copy(connectionStatus = status, showConnectionLabel = false) }
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

        startAutoConnect()
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
    }

    // ── Mark tool ─────────────────────────────────────────────────────────────

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
            _uiState.update { state ->
                state.copy(
                    pendingMarkPoints = (state.pendingMarkPoints + GeoPoint(lat, lon)).toImmutableList()
                )
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
        viewModelScope.launch { sendGeoMark(mark) }
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
        val type = if (points.size >= 2) GeoMarkType.TRACK else GeoMarkType.POINT
        val mark = GeoMarkModel(
            id           = UUID.randomUUID().toString(),
            waypointId   = 0,
            type         = type,
            points       = points,
            authorNodeId = "",
            createdAt    = System.currentTimeMillis() / 1_000,
            expiresAt    = null,
            isSelf       = true,
        )
        viewModelScope.launch { sendGeoMark(mark) }
        _uiState.update { it.copy(pendingMarkPoints = persistentListOf()) }
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
        var autoConnectAttempted = false

        scanJob = scanDevices(NoParams)
            .onEach { devices ->
                if (autoConnectAttempted) return@onEach
                val target = lastDevice?.let { last -> devices.find { it.address == last.address } }
                if (target != null) {
                    autoConnectAttempted = true
                    _uiState.update { it.copy(foundDevices = persistentListOf()) }
                    viewModelScope.launch {
                        connectToDevice(ConnectToMeshDeviceParams(target.address, target.name))
                    }
                    scanJob?.cancel()
                } else {
                    // Accumulate discovered devices across scan restarts (deduplicate by address).
                    _uiState.update { current ->
                        val merged = (current.foundDevices + devices)
                            .distinctBy { it.address }
                            .toImmutableList()
                        current.copy(foundDevices = merged)
                    }
                }
            }
            .onCompletion { cause ->
                // cause != null means cancelled (Connecting/Connected path) — skip restart.
                // Always restart if scan expired naturally and auto-connect didn't fire,
                // so the device list stays fresh.
                if (cause == null && !autoConnectAttempted) {
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
            // TODO: wire to follow-user / map lock toggle when implemented
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_target,    label = "привязка",     onClick = {}),
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
    private fun buildRightColumn(state: MainUiState, nav: HudNavCallbacks): HudColumnConfig =
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
                    button = HudButtonSlot(iconRes = R.drawable.ic_settings, label = "настройки", onClick = nav.onSettingsClick),
                    info = emptyInfoSlot(),
                ),
                HudRowConfig(
                    button = HudButtonSlot(iconRes = R.drawable.ic_mesh,     label = "сетка",     onClick = nav.onMeshClick),
                    info = emptyInfoSlot(),
                ),
                // TODO: confirm icon for "метки" — using ic_marks as closest available match
                HudRowConfig(
                    button = HudButtonSlot(iconRes = R.drawable.ic_marks,    label = "метки",     onClick = nav.onMarkersClick),
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
            if (state.showConnectionLabel)
                HudInfoSlot(content = "Сопряжено с ${status.shortName}", color = Color.Green)
            else
                emptyInfoSlot()
        else -> emptyInfoSlot()
    }

    private fun buildNodeStatusColor(state: MainUiState): Color {
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
}
