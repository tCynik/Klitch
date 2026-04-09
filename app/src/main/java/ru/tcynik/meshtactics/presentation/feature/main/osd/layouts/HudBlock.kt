package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudSide

@Composable
fun HudBlock(
    config: HudColumnConfig,
    side: HudSide,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (side) {
            HudSide.Left -> {
                HudButtonColumn(slots = config.buttons)
                HudInfoColumn(slots = config.infoItems)
            }
            HudSide.Right -> {
                HudInfoColumn(slots = config.infoItems)
                HudButtonColumn(slots = config.buttons)
            }
        }
    }
}
