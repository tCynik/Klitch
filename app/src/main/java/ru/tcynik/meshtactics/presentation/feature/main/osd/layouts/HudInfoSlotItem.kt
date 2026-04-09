package ru.tcynik.meshtactics.presentation.feature.main.osd.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudInfoSlot

// Renders a single info slot.
// Hidden slot (content = null): invisible placeholder that reserves the same space as a real slot.
@Composable
fun HudInfoSlotItem(slot: HudInfoSlot, modifier: Modifier = Modifier) {
    if (slot.content == null) {
        Box(modifier = modifier)
        return
    }

    Box(
        modifier = modifier.fillMaxHeight(),
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
