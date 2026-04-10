package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.layouts.HudButtonSlotItem
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig

// Portrait HUD — temporary layout.
// Info slots are hidden; buttons are placed in two columns on left and right edges.
// Will be redesigned when the portrait UX is properly specified.
@Composable
fun HudPortraitControlsLayer(
    config: HudConfig,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start,
        ) {
            config.left.rows.forEach { rowConfig ->
                HudButtonSlotItem(
                    slot = rowConfig.button,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.End,
        ) {
            config.right.rows.forEach { rowConfig ->
                HudButtonSlotItem(
                    slot = rowConfig.button,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
