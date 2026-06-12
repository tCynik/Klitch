package ru.tcynik.klitch.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.ui.theme.IconActive
import ru.tcynik.klitch.ui.theme.IconDisabled
import ru.tcynik.klitch.ui.theme.IconInactive

// Squared icon button with a rounded-rect outline frame.
// Frame is drawn here; icon files must NOT include the frame path.
//
// States:
//   enabled=true,  selected=null  → regular button   → primary color
//   enabled=true,  selected=true  → toggle on         → primary color
//   enabled=true,  selected=false → toggle off        → onSurface @ 45%
//   enabled=false                 → disabled          → onSurface @ 38%
@OptIn(ExperimentalFoundationApi::class)
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
    // null  → color driven by enabled/selected state (default)
    // set   → overrides the computed content color (e.g. GPS signal level tint)
    tintOverride: Color? = null,
    // true  → icon is drawn with its own intrinsic colors (no tint applied to the icon)
    //         frame still uses contentColor
    preserveIconColors: Boolean = false,
    // clockwise rotation applied to the icon only (degrees); 0 = no rotation
    iconRotationDegrees: Float = 0f,
    onLongClick: (() -> Unit)? = null,
) {
    val strokeWidth = 3.dp
    val cornerRadius = 16.dp

    // TODO: switch to theme-aware colors based on isSystemInDarkTheme() or a parameter
    val activeColor = IconActive
    val inactiveColor = IconInactive
    val disabledColor = IconDisabled

    val contentColor = tintOverride ?: when {
        !enabled -> disabledColor
        selected == false -> inactiveColor
        else -> activeColor
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .wrapContentWidth()
            .background(
                Color.Black.copy(alpha = 0.1f), MaterialTheme.shapes.large
            )
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        val squareSize = minOf(maxWidth, maxHeight)
        Canvas(modifier = Modifier.size(squareSize)) {
            val half = strokeWidth.toPx() / 2f
            drawRoundRect(
                color = contentColor,
                topLeft = Offset(half, half),
                size = Size(
                    this.size.width - strokeWidth.toPx(),
                    this.size.height - strokeWidth.toPx()
                ),
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
            tint = if (preserveIconColors) Color.Unspecified else contentColor,
            modifier = Modifier
                .size(squareSize * 0.70f)
                .rotate(iconRotationDegrees),
        )
    }
}
