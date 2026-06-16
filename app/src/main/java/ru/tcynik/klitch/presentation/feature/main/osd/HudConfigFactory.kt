package ru.tcynik.klitch.presentation.feature.main.osd

import ru.tcynik.klitch.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudInfoSlot
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudRowConfig

fun emptyButtonSlot() =
    HudButtonSlot(
        iconRes = null,
        onClick = {})
fun emptyInfoSlot() =
    HudInfoSlot(
        content = null
    )
fun emptyHudRowConfig() =
    HudRowConfig(
        button = emptyButtonSlot(),
        info = emptyInfoSlot(),
    )
fun emptyHudColumn() =
    HudColumnConfig(
        rows = List(5) { emptyHudRowConfig() },
    )
fun emptyHudConfig() =
    HudConfig(
        left = emptyHudColumn(),
        right = emptyHudColumn()
    )
