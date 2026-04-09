package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot

@Composable
fun HudButtonColumn(
    slots: List<HudButtonSlot>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
    ) {
        slots.forEachIndexed { index, slot ->
            if (index > 0)
                Spacer(
                    modifier = Modifier.height(20.dp)
                )
            HudButtonSlotItem(slot, modifier = Modifier.weight(1f))
        }
    }
}
