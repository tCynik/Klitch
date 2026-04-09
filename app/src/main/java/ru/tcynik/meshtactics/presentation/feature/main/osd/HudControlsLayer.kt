package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.layouts.HudBlock
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudSide

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
            .padding(8.dp)
            .padding(start = 32.dp),
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
