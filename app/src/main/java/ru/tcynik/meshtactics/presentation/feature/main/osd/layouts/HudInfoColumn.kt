package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot

// TODO: info column width 100.dp is provisional — adjust after content is defined
val INFO_COLUMN_WIDTH = 100.dp

@Composable
fun HudInfoColumn(
    slots: List<HudInfoSlot>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(INFO_COLUMN_WIDTH)
            .fillMaxHeight(),
    ) {
        slots.forEach { slot ->
            HudInfoSlotItem(slot, modifier = Modifier.weight(1f))
        }
    }
}
