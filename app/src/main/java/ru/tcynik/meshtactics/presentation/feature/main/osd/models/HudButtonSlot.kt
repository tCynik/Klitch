package ru.tcynik.meshtactics.presentation.feature.main.osd.models

import androidx.annotation.DrawableRes

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
