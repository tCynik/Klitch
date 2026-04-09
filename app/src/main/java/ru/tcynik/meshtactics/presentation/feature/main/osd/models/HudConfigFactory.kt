package ru.tcynik.meshtactics.presentation.feature.main.osd.models

// Factory helpers — produce empty column configs for use as defaults.
fun emptyButtonSlot() = HudButtonSlot(iconRes = null, label = "", onClick = {})
fun emptyInfoSlot() = HudInfoSlot(content = null)
fun emptyHudColumn() = HudColumnConfig(
    buttons = List(5) { emptyButtonSlot() },
    infoItems = List(5) { emptyInfoSlot() },
)
fun emptyHudConfig() = HudConfig(left = emptyHudColumn(), right = emptyHudColumn())
