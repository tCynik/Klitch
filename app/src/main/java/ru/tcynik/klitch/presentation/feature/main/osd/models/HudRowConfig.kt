package ru.tcynik.klitch.presentation.feature.main.osd.models

// Configuration for one HUD block (left or right).
// Each row pairs a button slot with its corresponding info slot.
data class HudColumnConfig(
    val rows: List<HudRowConfig>,
) {
    init {
        require(rows.size == 5) {
            "HudColumnConfig.rows must have exactly 5 slots, got ${rows.size}"
        }
    }
}

data class HudRowConfig(
    val button: HudButtonSlot,
    val info: HudInfoSlot,
)
