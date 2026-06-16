package ru.tcynik.klitch.presentation.feature.main.osd

import androidx.compose.ui.graphics.Color
import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.location.model.GpsSignalLevel
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.presentation.ui.UiText
import ru.tcynik.klitch.presentation.util.toMeshtasticDisplayShortName
import ru.tcynik.klitch.presentation.feature.main.ConnectionUiState
import ru.tcynik.klitch.presentation.feature.main.GeoMarkUiState
import ru.tcynik.klitch.presentation.feature.main.HudNavCallbacks
import ru.tcynik.klitch.presentation.feature.main.MainUiState
import ru.tcynik.klitch.presentation.feature.main.osd.models.DrawerMenuItem
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudInfoSlot
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudRowConfig
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudUiState
import ru.tcynik.klitch.presentation.feature.main.osd.models.MenuDrawerUiState

// BLE RSSI threshold separating low signal (red) from medium/high signal (green).
private const val RSSI_LOW_THRESHOLD = -90

object HudStateMapper {

    fun buildHudConfig(
        mainState: MainUiState,
        connState: ConnectionUiState,
        geoMarkState: GeoMarkUiState,
        nav: HudNavCallbacks,
    ): HudConfig = HudConfig(
        left = buildLeftColumn(mainState, geoMarkState, nav),
        right = buildRightColumn(mainState, connState, geoMarkState, nav),
    )

    fun buildHudUiState(
        mainState: MainUiState,
        connState: ConnectionUiState,
        geoMarkState: GeoMarkUiState,
        trackState: TrackRecordingState,
        nav: HudNavCallbacks,
    ): HudUiState = HudUiState(
        menuDrawer = HudRowConfig(
            button = HudButtonSlot(iconRes = R.drawable.ic_menu, label = R.string.hud_label_menu, onClick = nav.onToggleMenuDrawer),
            info = emptyInfoSlot(),
        ),
        zoomIn   = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_zoom_in,  label = R.string.hud_label_zoom_in,  onClick = {}), info = emptyInfoSlot()),
        zoomOut  = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_zoom_out, label = R.string.hud_label_zoom_out, onClick = {}), info = emptyInfoSlot()),
        compass  = HudRowConfig(
            button = buildCompassButton(mainState, nav),
            info = if (!mainState.isNorthLocked)
                HudInfoSlot(content = UiText.Raw("${mainState.mapBearing.toInt()}°"), color = Color.Red)
            else
                emptyInfoSlot(),
        ),
        target   = HudRowConfig(
            button = HudButtonSlot(iconRes = R.drawable.ic_target, label = R.string.hud_label_follow, selected = mainState.isFollowMeActive, onClick = nav.onFollowMeToggle),
            info = emptyInfoSlot(),
        ),
        markTool = HudRowConfig(
            button = HudButtonSlot(
                iconRes  = R.drawable.ic_marks_tool,
                label    = R.string.hud_label_marks,
                selected = geoMarkState.markToolActive,
                onClick  = nav.onToggleMarkTool,
            ),
            info = emptyInfoSlot(),
        ),
        mapTools = HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_map_tools, label = R.string.hud_label_tools, onClick = {}), info = emptyInfoSlot()),
        gps      = HudRowConfig(button = buildSatelliteButton(mainState), info = emptyInfoSlot()),
        radio    = HudRowConfig(
            button = buildRadioButton(mainState, connState, nav),
            info = buildConnectionInfoSlot(connState),
        ),
        marks    = HudRowConfig(button = buildMarksButton(geoMarkState, nav), info = emptyInfoSlot()),
        chat     = HudRowConfig(
            button = HudButtonSlot(
                iconRes   = R.drawable.ic_chat,
                label     = R.string.hud_label_chats,
                onClick   = nav.onChatClick,
                infoBadge = mainState.unreadChatCount.takeIf { it > 0 }?.toString(),
            ),
            info = emptyInfoSlot(),
        ),
        trackRecord = HudRowConfig(
            button = HudButtonSlot(
                iconRes  = R.drawable.ic_track_record,
                label    = R.string.hud_label_track,
                selected = trackState is TrackRecordingState.Recording,
                onClick  = nav.onToggleTrackRecordingSheet,
            ),
            info = emptyInfoSlot(),
        ),
    )

    fun buildMenuDrawerUiState(
        mainState: MainUiState,
        isSosActive: Boolean,
        nav: HudNavCallbacks,
    ): MenuDrawerUiState = MenuDrawerUiState(
        isOpen = mainState.menuDrawerOpen,
        items = listOf(
            DrawerMenuItem(
                iconRes = R.drawable.ic_radio,
                label = R.string.hud_label_radio,
                onClick = { nav.onRadioClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_mesh,
                label = R.string.hud_label_nodes,
                onClick = { nav.onMeshClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_settings,
                label = R.string.hud_label_settings_main,
                onClick = { nav.onMainSettingsClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_maps,
                label = R.string.hud_label_settings_map,
                onClick = { nav.onMapSettingsClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_night,
                label = R.string.hud_label_settings_display,
                onClick = { nav.onDisplaySettingsClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_man,
                label = R.string.hud_label_settings_user,
                onClick = { nav.onUserSettingsClick(); nav.onToggleMenuDrawer() },
            ),
            DrawerMenuItem(
                iconRes = R.drawable.ic_marks,
                label = R.string.hud_label_settings_marks,
                onClick = { nav.onGeoMarksList(); nav.onToggleMenuDrawer() },
            ),
        ),
        onDismiss = nav.onToggleMenuDrawer,
        isSosActive = isSosActive,
        onSosClick = nav.onSosClick,
    )

    fun buildCompassButton(mainState: MainUiState, nav: HudNavCallbacks): HudButtonSlot {
        val rotated = !mainState.isNorthLocked
        return HudButtonSlot(
            iconRes = if (rotated) R.drawable.ic_compass_rotated else R.drawable.ic_compass,
            label = R.string.hud_label_bearing,
            preserveIconColors = rotated,
            iconRotationDegrees = if (rotated) -mainState.mapBearing - 45f else 0f,
            selected = when {
                mainState.isCourseUpActive -> true
                mainState.isNorthLocked -> false
                else -> null
            },
            onClick = nav.onCompassTap,
            onLongClick = null,
        )
    }

    fun buildConnectionInfoSlot(connState: ConnectionUiState): HudInfoSlot =
        when (val status = connState.connectionStatus) {
            MeshConnectionStatus.Scanning ->
                if (connState.callsignRequired)
                    HudInfoSlot(content = UiText.Static(R.string.hud_info_set_callsign), color = Color.Red)
                else if (connState.foundDevices.isNotEmpty())
                    HudInfoSlot(content = UiText.Static(R.string.hud_info_select_node), color = Color.Black)
                else
                    HudInfoSlot(content = UiText.Static(R.string.hud_info_scanning), color = Color.Red)
            is MeshConnectionStatus.Connecting ->
                HudInfoSlot(
                    content = UiText.Dynamic(R.string.hud_info_pairing, status.deviceName.toMeshtasticDisplayShortName()),
                    color = Color.Black,
                )
            is MeshConnectionStatus.Connected ->
                if (connState.syncRequired)
                    HudInfoSlot(content = UiText.Static(R.string.hud_info_sync_required), color = Color.Red)
                else if (connState.showConnectionLabel)
                    HudInfoSlot(
                        content = UiText.Dynamic(R.string.hud_info_connected, status.shortName.toMeshtasticDisplayShortName()),
                        color = Color.Black,
                    )
                else if (status.batteryLevel in 1..100)
                    HudInfoSlot(
                        content = UiText.Raw("🔋${status.batteryLevel}%"),
                        color = if (status.batteryLevel < 20) Color.Red else Color.Black,
                    )
                else
                    emptyInfoSlot()
            else ->
                when (connState.syncCyclePhase) {
                    NodeSyncCyclePhase.Syncing ->
                        HudInfoSlot(content = UiText.Static(R.string.mesh_status_syncing), color = Color.Black)
                    NodeSyncCyclePhase.Rebooting ->
                        HudInfoSlot(content = UiText.Static(R.string.mesh_status_rebooting), color = Color.Black)
                    NodeSyncCyclePhase.WaitingForNode ->
                        HudInfoSlot(content = UiText.Static(R.string.mesh_status_waiting), color = Color.Black)
                    NodeSyncCyclePhase.Idle ->
                        if (connState.isRebooting)
                            HudInfoSlot(content = UiText.Static(R.string.mesh_status_rebooting), color = Color.Black)
                        else
                            emptyInfoSlot()
                }
        }

    fun buildNodeStatusColor(connState: ConnectionUiState): Color {
        if (!connState.networkEnabled) return Color.Gray
        if (connState.isRebooting) return Color.Yellow
        return when (val status = connState.connectionStatus) {
            is MeshConnectionStatus.Connected ->
                if (status.rssi < RSSI_LOW_THRESHOLD) Color.Yellow else Color.Green
            MeshConnectionStatus.Disconnected,
            is MeshConnectionStatus.Error,
            MeshConnectionStatus.DeviceSleep -> Color.Red
            MeshConnectionStatus.Scanning ->
                if (connState.foundDevices.isNotEmpty()) Color.Yellow else Color.Red
            is MeshConnectionStatus.Connecting -> Color.Yellow
        }
    }

    private fun buildSatelliteButton(mainState: MainUiState): HudButtonSlot = HudButtonSlot(
        iconRes      = R.drawable.ic_satellite,
        label        = R.string.hud_label_gps,
        onClick      = {},
        tintOverride = when (mainState.gpsStatus.signalLevel) {
            GpsSignalLevel.None   -> Color.Red
            GpsSignalLevel.Weak   -> Color.Yellow
            GpsSignalLevel.Strong -> Color.Green
        },
        infoBadge    = mainState.gpsStatus.accuracyMeters
            ?.let { it.toInt().coerceAtMost(99).toString() }
            .takeIf { it != "0" },
    )

    private fun buildRadioButton(mainState: MainUiState, connState: ConnectionUiState, nav: HudNavCallbacks): HudButtonSlot = HudButtonSlot(
        iconRes      = R.drawable.ic_radio,
        label        = R.string.hud_label_radio,
        onClick      = nav.onRadioClick,
        selected     = if (!connState.networkEnabled) false else null,
        tintOverride = buildNodeStatusColor(connState),
        infoBadge    = when (connState.connectionStatus) {
            is MeshConnectionStatus.Connected -> mainState.nodeMarkers.size.toString().take(2)
            else -> null
        }.takeIf { it != "0" },
    )

    private fun buildMarksButton(geoMarkState: GeoMarkUiState, nav: HudNavCallbacks): HudButtonSlot = HudButtonSlot(
        iconRes   = R.drawable.ic_marks,
        label     = R.string.hud_label_marks,
        selected  = if (geoMarkState.isMarksSheetVisible) true else null,
        onClick   = nav.onToggleGeoMarksSheet,
        infoBadge = geoMarkState.pendingMarkPoints.size.takeIf { it > 0 }?.toString(),
    )

    private fun buildLeftColumn(
        mainState: MainUiState,
        geoMarkState: GeoMarkUiState,
        nav: HudNavCallbacks,
    ): HudColumnConfig = HudColumnConfig(
        rows = listOf(
            HudRowConfig(button = buildCompassButton(mainState, nav), info = emptyInfoSlot()),
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_target, label = R.string.hud_label_follow, selected = mainState.isFollowMeActive, onClick = nav.onFollowMeToggle),
                info = emptyInfoSlot(),
            ),
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_marks_tool, label = R.string.hud_label_marks, selected = geoMarkState.markToolActive, onClick = nav.onToggleMarkTool),
                info = emptyInfoSlot(),
            ),
            HudRowConfig(button = HudButtonSlot(iconRes = R.drawable.ic_map_tools, label = R.string.hud_label_tools, onClick = {}), info = emptyInfoSlot()),
            HudRowConfig(button = buildSatelliteButton(mainState), info = emptyInfoSlot()),
        ),
    )

    private fun buildRightColumn(
        mainState: MainUiState,
        connState: ConnectionUiState,
        geoMarkState: GeoMarkUiState,
        nav: HudNavCallbacks,
    ): HudColumnConfig = HudColumnConfig(
        rows = listOf(
            HudRowConfig(button = buildRadioButton(mainState, connState, nav), info = buildConnectionInfoSlot(connState)),
            HudRowConfig(button = buildMarksButton(geoMarkState, nav), info = emptyInfoSlot()),
            HudRowConfig(
                button = HudButtonSlot(iconRes = R.drawable.ic_chat, label = R.string.hud_label_chats, onClick = nav.onChatClick, infoBadge = mainState.unreadChatCount.takeIf { it > 0 }?.toString()),
                info = emptyInfoSlot(),
            ),
            emptyHudRowConfig(),
            emptyHudRowConfig(),
        ),
    )
}
