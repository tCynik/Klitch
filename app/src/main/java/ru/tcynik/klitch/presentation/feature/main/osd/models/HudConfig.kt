package ru.tcynik.klitch.presentation.feature.main.osd.models

// Top-level HUD configuration passed to HudControlsLayer.
data class HudConfig(
    val left: HudColumnConfig,
    val right: HudColumnConfig,
)
