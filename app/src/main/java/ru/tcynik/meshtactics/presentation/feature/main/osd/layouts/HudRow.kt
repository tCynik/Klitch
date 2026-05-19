package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudRowConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudSide

@Composable
fun HudRow(
    config: HudRowConfig,
    side: HudSide,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.wrapContentWidth(),
    ) {
        when (side) {
            HudSide.Left -> {
                HudButtonSlotItem(
                    slot = config.button,
                    side = side,
                )
                HudInfoSlotItem(
                    slot = config.info,
                    side = side,
                    modifier = Modifier.width(100.dp)
                )
            }
            HudSide.Right -> {
                HudInfoSlotItem(
                    slot = config.info,
                    side = side,
                    modifier = Modifier.width(100.dp)
                )
                HudButtonSlotItem(
                    slot = config.button,
                    side = side,
                )
            }
        }
    }
}
