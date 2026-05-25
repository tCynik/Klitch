package ru.tcynik.meshtactics.presentation.feature.main.osd.models

// Portrait HUD state: named button+info pairs replace the generic slot list.
// Contains lambdas → lives in a separate StateFlow outside MainUiState.
data class HudUiState(
    // Left column (map tools) — menuDrawer pinned at top, zoomIn/zoomOut in middle, rest at bottom in portrait
    val menuDrawer: HudRowConfig,
    val zoomIn: HudRowConfig,
    val zoomOut: HudRowConfig,
    val compass: HudRowConfig,
    val target: HudRowConfig,
    val markTool: HudRowConfig,
    val mapTools: HudRowConfig,
    val gps: HudRowConfig,
    // Right column (main menu) — radio pinned to top in portrait, rest at bottom
    val radio: HudRowConfig,
    val marks: HudRowConfig,
    val chat: HudRowConfig,
)
