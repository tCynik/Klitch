package ru.tcynik.klitch.presentation.feature.main.osd

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.presentation.feature.main.osd.layouts.HudRow
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudSide
import ru.tcynik.klitch.presentation.feature.main.osd.models.HudUiState
import androidx.compose.foundation.layout.Arrangement

// Portrait HUD — fixed layout:
// Left column:  menuDrawer pinned at top, zoomIn/zoomOut in middle (SpaceBetween), map tools at bottom.
// Right column: radio button pinned to the top, remaining buttons at the bottom.
@Composable
fun HudPortraitControlsLayer(
    state: HudUiState,
    modifier: Modifier = Modifier,
    onCompassLongClick: (() -> Unit)? = null,
    onFollowMeClick: (() -> Unit)? = null,
    onZoomInClick: (() -> Unit)? = null,
    onZoomOutClick: (() -> Unit)? = null,
) {
    val compassConfig = if (onCompassLongClick != null)
        state.compass.copy(button = state.compass.button.copy(onLongClick = onCompassLongClick))
    else
        state.compass
    val targetConfig = if (onFollowMeClick != null)
        state.target.copy(button = state.target.button.copy(onClick = onFollowMeClick))
    else
        state.target
    val zoomInConfig = if (onZoomInClick != null)
        state.zoomIn.copy(button = state.zoomIn.button.copy(onClick = onZoomInClick))
    else
        state.zoomIn
    val zoomOutConfig = if (onZoomOutClick != null)
        state.zoomOut.copy(button = state.zoomOut.button.copy(onClick = onZoomOutClick))
    else
        state.zoomOut

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left — hamburger at top, zoom in/out in middle, map tools at bottom
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HudRow(config = state.menuDrawer, side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                Column(
                    horizontalAlignment = Alignment.Start,
                ) {
                    HudRow(config = zoomInConfig,  side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(4.dp))
                    HudRow(config = zoomOutConfig, side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                }
                Column(
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    HudRow(config = compassConfig,  side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(10.dp))
                    HudRow(config = targetConfig,   side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(10.dp))
                    HudRow(config = state.mapTools,      side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(10.dp))
                    HudRow(config = state.gps,           side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(10.dp))
                    HudRow(config = state.trackRecord,   side = HudSide.Left, modifier = Modifier.wrapContentWidth().height(60.dp))
                }
            }

            // Right — radio top, rest at bottom
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HudRow(config = state.radio, side = HudSide.Right, modifier = Modifier.wrapContentWidth().height(60.dp))
                Column {
                    HudRow(config = state.marks,    side = HudSide.Right, modifier = Modifier.wrapContentWidth().height(60.dp))
                    Spacer(Modifier.height(10.dp))
                    HudRow(config = state.chat,     side = HudSide.Right, modifier = Modifier.wrapContentWidth().height(60.dp))
                }
            }
        }
    }
}
