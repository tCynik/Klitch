package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot

// HUD button columns overlay — rendered on top of MapLibreLayer.
// Layout: [left block] ··· map ··· [right block]
// Each block: [button column | info column] (left) or [info column | button column] (right)
// Content is driven entirely by HudConfig; the layout itself is constant.
@Composable
fun HudControlsLayer(
    config: HudConfig,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HudBlock(config = config.left, side = HudSide.Left)
        HudBlock(config = config.right, side = HudSide.Right)
    }
}

private enum class HudSide { Left, Right }

@Composable
private fun HudBlock(config: HudColumnConfig, side: HudSide) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun HudButtonColumn(slots: List<HudButtonSlot>) {
    Column(
        modifier = Modifier.fillMaxHeight(),
    ) {
        slots.forEach { slot ->
            HudButtonSlotItem(slot, modifier = Modifier.weight(1f))
        }
    }
}

// TODO: info column width 100.dp is provisional — adjust after content is defined
private val INFO_COLUMN_WIDTH = 100.dp

@Composable
private fun HudInfoColumn(slots: List<HudInfoSlot>) {
    Column(
        modifier = Modifier
            .width(INFO_COLUMN_WIDTH)
            .fillMaxHeight(),
    ) {
        slots.forEach { slot ->
            HudInfoSlotItem(slot, modifier = Modifier.weight(1f))
        }
    }
}
