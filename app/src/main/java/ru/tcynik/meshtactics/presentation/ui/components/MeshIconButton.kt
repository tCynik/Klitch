package ru.tcynik.meshtactics.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.ui.theme.IconActive
import ru.tcynik.meshtactics.ui.theme.IconDisabled
import ru.tcynik.meshtactics.ui.theme.IconInactive

// Squared icon button with a rounded-rect outline frame.
// Frame is drawn here; icon files must NOT include the frame path.
//
// States:
//   enabled=true,  selected=null  → regular button   → primary color
//   enabled=true,  selected=true  → toggle on         → primary color
//   enabled=true,  selected=false → toggle off        → onSurface @ 45%
//   enabled=false                 → disabled          → onSurface @ 38%
@Composable
fun MeshIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // null  → regular button (no toggle state), color driven by enabled only
    // true  → toggle on  → Enabled color
    // false → toggle off → Unpressed color (button remains clickable)
    selected: Boolean? = null,
    size: Dp = 80.dp,
) {
    val strokeWidth = 3.dp
    val cornerRadius = 16.dp

    // TODO: switch to theme-aware colors based on isSystemInDarkTheme() or a parameter
    val activeColor = IconActive
    val inactiveColor = IconInactive
    val disabledColor = IconDisabled

    val contentColor = when {
        !enabled          -> disabledColor
        selected == false -> inactiveColor
        else              -> activeColor
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val half = strokeWidth.toPx() / 2f
            drawRoundRect(
                color = contentColor,
                topLeft = Offset(half, half),
                size = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx()),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.70f),
        )
    }
}
