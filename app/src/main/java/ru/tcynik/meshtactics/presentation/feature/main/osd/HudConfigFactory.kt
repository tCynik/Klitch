package ru.tcynik.meshtactics.presentation.feature.main.osd

// Factory helpers — produce empty column configs for use as defaults.
fun emptyButtonSlot() =
    _root_ide_package_.ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot(
        iconRes = null,
        label = "",
        onClick = {})
fun emptyInfoSlot() =
    _root_ide_package_.ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot(
        content = null
    )
fun emptyHudColumn() =
    _root_ide_package_.ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig(
        buttons = List(5) { emptyButtonSlot() },
        infoItems = List(5) { emptyInfoSlot() },
    )
fun emptyHudConfig() =
    _root_ide_package_.ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig(
        left = emptyHudColumn(),
        right = emptyHudColumn()
    )
