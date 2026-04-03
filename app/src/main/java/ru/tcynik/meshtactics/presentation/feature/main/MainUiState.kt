package ru.tcynik.meshtactics.presentation.feature.main

import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition

// TODO: replace default with GPS first-fix or user-configurable home position
private val DEFAULT_CAMERA_POSITION = MapCameraPosition(lat = 56.0184, lon = 92.8672, zoom = 10.0)

data class MainUiState(
    val tileUrlTemplate: String = "",
    val isLoading: Boolean = false,
    val initialCameraPosition: MapCameraPosition = DEFAULT_CAMERA_POSITION,
)
