package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.ui.components.MeshIconButton

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
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HudBlock(config = config.left, side = HudSide.Left)
        HudBlock(config = config.right, side = HudSide.Right)
    }
}

private enum class HudSide { Left, Right }

@Composable
private fun HudBlock(config: HudColumnConfig, side: HudSide) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (side) {
            HudSide.Left -> {
                HudButtonColumn(slots = config.buttons)
                HudInfoColumn(slots = config.infoItems)
            }
            HudSide.Right -> {
                HudInfoColumn(slots = config.infoItems)
                HudButtonColumn(slots = config.buttons)
            }
        }
    }
}

@Composable
private fun HudButtonColumn(slots: List<HudButtonSlot>) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        slots.forEach { slot -> HudButtonSlotItem(slot) }
    }
}

// TODO: info column width 100.dp is provisional — adjust after content is defined
private val INFO_COLUMN_WIDTH = 100.dp

@Composable
private fun HudInfoColumn(slots: List<HudInfoSlot>) {
    Column(
        modifier = Modifier
            .width(INFO_COLUMN_WIDTH)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        slots.forEach { slot -> HudInfoSlotItem(slot) }
    }
}

// Renders a single button slot.
// Empty slot (iconRes = null): invisible placeholder that reserves the same space as a real slot.
@Composable
private fun HudButtonSlotItem(slot: HudButtonSlot) {
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

// Renders a single info slot.
// Hidden slot (content = null): invisible placeholder that reserves the same space as a real slot.
@Composable
private fun HudInfoSlotItem(slot: HudInfoSlot) {
    if (slot.content == null) {
        Box(modifier = Modifier.height(80.dp))
        return
    }

    Box(
        modifier = Modifier.height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = slot.content,
            // TODO: color token for slot.color — provisional direct Color use until design system
            //       defines signal-quality semantic tokens (see hud-structure.md open question 1)
            color = slot.color ?: MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
