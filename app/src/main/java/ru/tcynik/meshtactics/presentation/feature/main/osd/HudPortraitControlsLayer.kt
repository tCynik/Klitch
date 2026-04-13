package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.layouts.HudBlock
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudSide

// Portrait HUD — layout with buttons and info slots.
// Layout: [left block] ··· map ··· [right block]
// Blocks are anchored to the bottom of the screen.
@Composable
fun HudPortraitControlsLayer(
    config: HudConfig,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HudBlock(
                    modifier = Modifier.wrapContentWidth(),
                    config = config.left,
                    side = HudSide.Left
                )
                HudBlock(
                    modifier = Modifier.wrapContentWidth(),
                    config = config.right,
                    side = HudSide.Right
                )
            }
        }
    }
}
