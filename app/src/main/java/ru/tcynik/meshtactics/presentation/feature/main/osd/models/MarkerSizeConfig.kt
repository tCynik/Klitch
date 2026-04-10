package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MarkerSizeConfig {
    val markerSize: Dp = 52.dp

    val userMarkerSize: Dp get() = markerSize
    val nodeMarkerRadius: Dp get() = markerSize / 3f
    val nodeMarkerStrokeWidth: Dp get() = nodeMarkerRadius / 4f
}
