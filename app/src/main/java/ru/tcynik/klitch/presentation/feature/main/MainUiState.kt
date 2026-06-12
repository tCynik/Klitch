package ru.tcynik.klitch.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.domain.location.model.GpsStatusModel
import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.domain.marker.model.NodeMarkerModel
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.presentation.feature.main.osd.models.OverlayRenderModel
import ru.tcynik.klitch.presentation.feature.main.osd.models.RecordedTrackRenderModel

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
    val geoMarkSizeLevel: Int = 5,
    val showGeoMarkNames: Boolean = false,
    val selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf(),
    val unreadChatCount: Int = 0,
    val showConnectionLabel: Boolean = false,
    // Devices found during BLE scan that are NOT the last connected device.
    // Shown in NodeSelectorPanel when non-empty and status == Scanning.
    // Accumulates across scan restarts; cleared on connect.
    val foundDevices: ImmutableList<MeshDeviceModel> = persistentListOf(),
    val geoMarks: ImmutableList<GeoMarkModel> = persistentListOf(),
    /** Выбранная метка на карте (контекстное меню); красная обводка как у черновика. */
    val selectedGeoMarkId: String? = null,
    /** Метка, ожидающая подтверждения удаления с карты. */
    val deleteConfirmMarkId: String? = null,
    val markToolActive: Boolean = false,
    val pendingMarkPoints: ImmutableList<GeoPoint> = persistentListOf(),
    /** Синхронно с [pendingMarkPoints]; не пересчитывать отдельно в UI. */
    val trackDraftDistanceLabel: String = "0.000/0.000км",
    val syncRequired: Boolean = false,
    val callsignRequired: Boolean = false,
    val isRebooting: Boolean = false,
    val syncCyclePhase: NodeSyncCyclePhase = NodeSyncCyclePhase.Idle,
    val menuDrawerOpen: Boolean = false,
    val isFollowMeActive: Boolean = false,
    val isCourseUpActive: Boolean = false,
    val zoomAtCourseUpActivation: Double? = null,
    // true when map was explicitly snapped to north (compass tap) and not rotated since
    val isNorthLocked: Boolean = true,
    // current map camera bearing in degrees [0, 360); used to rotate compass icon
    val mapBearing: Float = 0f,
    val networkEnabled: Boolean = true,
    val recordedTracks: ImmutableList<RecordedTrackRenderModel> = persistentListOf(),
    val showSosRestoredDialog: Boolean = false,
    val isSosActive: Boolean = false,
    val showSosTriggerDialog: Boolean = false,
    val showSosCancelDialog: Boolean = false,
)
