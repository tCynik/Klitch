package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MarkerSizeConfig {
    /**
     * Compute marker size from a level (1–10).
     * Level 1 = 36.dp, each step adds 4.dp.
     */
    fun fromLevel(level: Int): Dp = (36 + (level - 1) * 4).dp

    // Default size for backward compatibility
    val markerSize: Dp get() = fromLevel(5)

    val userMarkerSize: Dp get() = markerSize
    val nodeMarkerRadius: Dp get() = markerSize / 2f
    val nodeMarkerStrokeWidth: Dp get() = nodeMarkerRadius / 4f
}
