package ru.tcynik.meshtactics.presentation.feature.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus

// TODO: replace default with GPS first-fix or user-configurable home position
private val DEFAULT_CAMERA_POSITION = MapCameraPosition(lat = 56.0184, lon = 92.8672, zoom = 10.0)

data class MainUiState(
    val tileUrlTemplate: String = "",
    val isLoading: Boolean = false,
    val initialCameraPosition: MapCameraPosition = DEFAULT_CAMERA_POSITION,
    val nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
    val connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    val gpsStatus: GpsStatusModel = GpsStatusModel.None,
)
