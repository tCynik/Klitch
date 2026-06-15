package ru.tcynik.klitch.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.domain.location.model.GpsStatusModel
import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.marker.model.NodeMarkerModel
import ru.tcynik.klitch.presentation.feature.main.osd.models.OverlayRenderModel
import ru.tcynik.klitch.presentation.feature.main.osd.models.RecordedTrackRenderModel

// TODO: replace default with GPS first-fix or user-configurable home position
private val DEFAULT_CAMERA_POSITION = MapCameraPosition(lat = 56.0184, lon = 92.8672, zoom = 10.0)

data class MainUiState(
    val tileUrlTemplate: String = "",
    val isLoading: Boolean = false,
    val initialCameraPosition: MapCameraPosition = DEFAULT_CAMERA_POSITION,
    val nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
    val gpsStatus: GpsStatusModel = GpsStatusModel.None,
    val markerSizeLevel: Int = 5,
    val geoMarkSizeLevel: Int = 5,
    val showGeoMarkNames: Boolean = false,
    val selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf(),
    val unreadChatCount: Int = 0,
    val menuDrawerOpen: Boolean = false,
    val isFollowMeActive: Boolean = false,
    val isCourseUpActive: Boolean = false,
    val zoomAtCourseUpActivation: Double? = null,
    // true when map was explicitly snapped to north (compass tap) and not rotated since
    val isNorthLocked: Boolean = true,
    // current map camera bearing in degrees [0, 360); used to rotate compass icon
    val mapBearing: Float = 0f,
    val recordedTracks: ImmutableList<RecordedTrackRenderModel> = persistentListOf(),
)
