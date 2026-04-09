package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.ui.components.MeshIconButton

// Renders a single button slot.
// Empty slot (iconRes = null): invisible placeholder that reserves the same space as a real slot.
@Composable
fun HudButtonSlotItem(slot: HudButtonSlot, modifier: Modifier = Modifier) {
    if (slot.iconRes == null) {
        // Reserve space without rendering anything
        Box(modifier = modifier)
        return
    }

    // Resolve ImageVector here in composable scope — ViewModel stores @DrawableRes Int
    val icon: ImageVector = ImageVector.vectorResource(slot.iconRes)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MeshIconButton(
            icon = icon,
            onClick = slot.onClick,
            enabled = slot.enabled,
            selected = slot.selected,
        )
        // TODO: gap between button and label is 4.dp (provisional, confirm with /ui-designer)
        Spacer(modifier = Modifier.height(4.dp))
//        Text(
//            text = slot.label,
//            // TODO: typography token is labelSmall (provisional, confirm with /ui-designer)
//            style = MaterialTheme.typography.labelSmall,
//        )
    }
}
