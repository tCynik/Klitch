package ru.tcynik.meshtactics.presentation.feature.main.osd.models

/**
 * Emitted when a long-tap lands within DRAFT_POINT_TOUCH_RADIUS_DP of a pending draft point.
 * MainScreen observes this and shows a DropdownMenu anchored at (screenX, screenY).
 */
data class GeoMarkContextMenuEvent(
    val pointIndex: Int,
    val screenX: Float,
    val screenY: Float,
)
