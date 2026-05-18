package ru.tcynik.meshtactics.presentation.feature.main.osd.models

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
    // null  → color driven by enabled/selected state (default)
    // set   → overrides the computed content color (e.g. GPS signal level tint)
    val tintOverride: Color? = null,
    // true  → icon drawn with its own intrinsic colors; frame still uses contentColor
    val preserveIconColors: Boolean = false,
    // clockwise rotation applied to the icon only (degrees); 0 = no rotation
    val iconRotationDegrees: Float = 0f,
    // Additional info badge (max 2 characters) displayed as a circle overlay
    val infoBadge: String? = null,
    val onLongClick: (() -> Unit)? = null,
)
