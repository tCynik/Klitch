package ru.tcynik.meshtactics.presentation.feature.main.osd.models

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
