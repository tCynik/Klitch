package ru.tcynik.klitch.presentation.feature.main.osd.models

import androidx.compose.ui.graphics.Color

// One slot in the info column.
// content = null → slot is hidden (space reserved).
data class HudInfoSlot(
    val content: String?,
    // TODO: replace with a semantic color token once design system defines signal-quality colors
    val color: Color? = null,
)
