package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
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
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.location.model.GpsSignalLevel
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudRowConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyHudColumn
import ru.tcynik.meshtactics.presentation.feature.main.osd.emptyInfoSlot

// BLE RSSI threshold separating low signal (red) from medium/high signal (green).
// Adjust based on field experience; -90 dBm is the standard Meshtastic convention.
private const val RSSI_LOW_THRESHOLD = -90

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
    observeNodeMarkers: ObserveNodeMarkersUseCase,
    observeConnectionStatus: ObserveConnectionStatusUseCase,
    observeGpsStatus: ObserveGpsStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
            )
        }

        observeNodeMarkers(NoParams)
            .onEach { markers ->
                _uiState.update { it.copy(nodeMarkers = markers.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams)
            .onEach { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
            .launchIn(viewModelScope)

        observeGpsStatus(NoParams)
            .onEach { gpsStatus ->
                _uiState.update { it.copy(gpsStatus = gpsStatus) }
            }
            .launchIn(viewModelScope)
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
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
            // TODO: wire to track recording toggle when implemented
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_edit,      label = "запись трека", onClick = {}),
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
                ),
                info = buildGpsAccuracyInfoSlot(state),
            ),
        ),
    )

    private fun buildGpsAccuracyInfoSlot(state: MainUiState): HudInfoSlot {
        val color = when (state.gpsStatus.signalLevel) {
            GpsSignalLevel.None   -> Color.Red
            GpsSignalLevel.Weak   -> Color.Yellow
            GpsSignalLevel.Strong -> Color.Green
        }
        val text = state.gpsStatus.accuracyMeters?.let { "%.0fm".format(it) } ?: "--"
        return HudInfoSlot(content = text, color = color)
    }

    // Right column — main menu.
    // Info row 0: node status indicator (connection quality + peer count).
    // Button rows 0–4: radio, settings, mesh, markers, chat.
    private fun buildRightColumn(state: MainUiState, nav: HudNavCallbacks): HudColumnConfig =
        HudColumnConfig(
            rows = listOf(
                HudRowConfig(
                    button = HudButtonSlot(iconRes = R.drawable.ic_radio,    label = "радио",     onClick = nav.onRadioClick),
                    info = buildNodeStatusInfoSlot(state),
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
                    button = HudButtonSlot(iconRes = R.drawable.ic_chat,     label = "чаты",      onClick = nav.onChatClick),
                    info = emptyInfoSlot(),
                ),
            ),
        )

    private fun buildNodeStatusInfoSlot(state: MainUiState): HudInfoSlot {
        return when (val status = state.connectionStatus) {
            is MeshConnectionStatus.Connected -> {
                // TODO: color token — using raw Color until design system defines signal-quality tokens
                val color = if (status.rssi < RSSI_LOW_THRESHOLD) Color.Red else Color.Green
                HudInfoSlot(content = state.nodeMarkers.size.toString(), color = color)
            }
            else -> HudInfoSlot(content = "--", color = Color.Gray)
        }
    }
}
