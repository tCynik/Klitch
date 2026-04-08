package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

// One slot in the button column.
// iconRes = null → empty slot: space is reserved but nothing is rendered.
data class HudButtonSlot(
    @DrawableRes val iconRes: Int?,
    // Short caption rendered below the button.
    // TODO: typography token — using labelSmall (provisional, confirm with /ui-designer)
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    // null  → regular button (no toggle state)
    // true  → toggle on
    // false → toggle off
    val selected: Boolean? = null,
)

// One slot in the info column.
// content = null → slot is hidden (space reserved).
data class HudInfoSlot(
    val content: String?,
    // TODO: replace with a semantic color token once design system defines signal-quality colors
    val color: Color? = null,
)

// Configuration for one HUD block (left or right).
// Invariant: both lists must contain exactly 5 elements.
data class HudColumnConfig(
    val buttons: List<HudButtonSlot>,
    val infoItems: List<HudInfoSlot>,
) {
    init {
        require(buttons.size == 5) {
            "HudColumnConfig.buttons must have exactly 5 slots, got ${buttons.size}"
        }
        require(infoItems.size == 5) {
            "HudColumnConfig.infoItems must have exactly 5 slots, got ${infoItems.size}"
        }
    }
}

// Top-level HUD configuration passed to HudControlsLayer.
data class HudConfig(
    val left: HudColumnConfig,
    val right: HudColumnConfig,
)

// Factory helpers — produce empty column configs for use as defaults.
fun emptyButtonSlot() = HudButtonSlot(iconRes = null, label = "", onClick = {})
fun emptyInfoSlot() = HudInfoSlot(content = null)
fun emptyHudColumn() = HudColumnConfig(
    buttons = List(5) { emptyButtonSlot() },
    infoItems = List(5) { emptyInfoSlot() },
)
fun emptyHudConfig() = HudConfig(left = emptyHudColumn(), right = emptyHudColumn())
