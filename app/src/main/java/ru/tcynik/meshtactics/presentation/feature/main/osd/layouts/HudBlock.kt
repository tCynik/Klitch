package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudColumnConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudSide

@Composable
fun HudBlock(
    config: HudColumnConfig,
    side: HudSide,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        config.rows.forEachIndexed { index, rowConfig ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            HudRow(
                config = rowConfig,
                side = side,
                modifier = Modifier.wrapContentWidth()
                    .height(70.dp),
            )
        }
    }
}
