package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        val controlsHeight = maxHeight * 0.4f

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                modifier = Modifier.height(controlsHeight).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .height(controlsHeight)
                        .wrapContentWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.Start,
                ) {
                    config.left.rows.forEachIndexed { index, config ->
                        if (index > 0)
                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                        HudButtonSlotItem(
                            slot = config.button,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .height(controlsHeight)
                        .wrapContentWidth(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.End,
                ) {
                    config.right.rows.forEachIndexed { index, config ->
                        if (index > 0)
                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                        HudButtonSlotItem(
                            slot = config.button,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
