package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudButtonSlot
import ru.tcynik.meshtactics.presentation.ui.components.MeshIconButton

// Renders a single button slot.
// Empty slot (iconRes = null): invisible placeholder that reserves the same space as a real slot.
@Composable
fun HudButtonSlotItem(slot: HudButtonSlot) {
    // TODO: gap between button and label is 4.dp (provisional, confirm with /ui-designer)
    val slotHeight = 80.dp + 4.dp + MaterialTheme.typography.labelSmall.lineHeight.value.dp

    if (slot.iconRes == null) {
        // Reserve space without rendering anything
        Box(modifier = Modifier.height(slotHeight))
        return
    }

    // Resolve ImageVector here in composable scope — ViewModel stores @DrawableRes Int
    val icon: ImageVector = ImageVector.vectorResource(slot.iconRes)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MeshIconButton(
            icon = icon,
            onClick = slot.onClick,
            enabled = slot.enabled,
            selected = slot.selected,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = slot.label,
            // TODO: typography token is labelSmall (provisional, confirm with /ui-designer)
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
