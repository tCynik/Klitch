package ru.tcynik.meshtactics.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.OverlayRenderModel

// TODO: replace default with GPS first-fix or user-configurable home position
private val DEFAULT_CAMERA_POSITION = MapCameraPosition(lat = 56.0184, lon = 92.8672, zoom = 10.0)

data class MainUiState(
    val tileUrlTemplate: String = "",
    val isLoading: Boolean = false,
    val initialCameraPosition: MapCameraPosition = DEFAULT_CAMERA_POSITION,
    val nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
    val connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    val gpsStatus: GpsStatusModel = GpsStatusModel.None,
    val markerSizeLevel: Int = 5,
    val selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf(),
    val unreadChatCount: Int = 0,
    val showConnectionLabel: Boolean = false,
    // Devices found during BLE scan that are NOT the last connected device.
    // Shown in NodeSelectorPanel when non-empty and status == Scanning.
    // Accumulates across scan restarts; cleared on connect.
    val foundDevices: ImmutableList<MeshDeviceModel> = persistentListOf(),
)
