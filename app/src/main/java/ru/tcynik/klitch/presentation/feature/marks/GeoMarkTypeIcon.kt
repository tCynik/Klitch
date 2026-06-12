package ru.tcynik.klitch.presentation.feature.marks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.TrackEndType

/** Shape preview for POINT marks — matches GeoMarksSheet ShapeIcon with fill color. */
@Composable
fun GeoMarkShapeIcon(
    shape: GeoMarkShape,
    colorArgb: Int,
    modifier: Modifier = Modifier,
) {
    val color = Color(colorArgb)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val side = size.minDimension
        val offsetX = (size.width - side) / 2f
        val offsetY = (size.height - side) / 2f
        val pad = side * 0.08f
        val centerX = offsetX + side / 2f
        val centerY = offsetY + side / 2f
        when (shape) {
            GeoMarkShape.CIRCLE -> drawCircle(
                color = color,
                radius = side / 2f - pad,
                center = Offset(centerX, centerY),
            )
            GeoMarkShape.SQUARE -> drawRect(
                color = color,
                topLeft = Offset(offsetX + pad, offsetY + pad),
                size = Size(side - pad * 2f, side - pad * 2f),
            )
            GeoMarkShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(centerX, offsetY + pad)
                    lineTo(offsetX + side - pad, offsetY + side - pad)
                    lineTo(offsetX + pad, offsetY + side - pad)
                    close()
                }
                drawPath(path, color = color)
            }
        }
    }
}

/** Track end preview for TRACK marks — matches GeoMarksSheet TrackEndTypeIcon. */
@Composable
fun GeoMarkTrackEndIcon(
    endType: TrackEndType,
    colorArgb: Int,
    modifier: Modifier = Modifier,
) {
    val strokeColor = Color(colorArgb)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val sw = h * 0.14f
        when (endType) {
            TrackEndType.NONE -> {
                drawLine(strokeColor, Offset(w * 0.10f, cy), Offset(w * 0.90f, cy), sw)
            }
            TrackEndType.ARROW -> {
                drawLine(strokeColor, Offset(w * 0.10f, cy), Offset(w * 0.88f, cy), sw)
                drawLine(strokeColor, Offset(w * 0.88f, cy), Offset(w * 0.62f, cy - h * 0.28f), sw)
                drawLine(strokeColor, Offset(w * 0.88f, cy), Offset(w * 0.62f, cy + h * 0.28f), sw)
            }
            TrackEndType.SMALL_FILLED_CIRCLE -> {
                drawLine(strokeColor, Offset(w * 0.10f, cy), Offset(w * 0.72f, cy), sw)
                drawCircle(strokeColor, radius = h * 0.15f, center = Offset(w * 0.84f, cy))
            }
            TrackEndType.LARGE_EMPTY_CIRCLE -> {
                drawLine(strokeColor, Offset(w * 0.10f, cy), Offset(w * 0.65f, cy), sw)
                drawCircle(strokeColor, radius = h * 0.22f, center = Offset(w * 0.80f, cy), style = Stroke(width = sw))
            }
        }
    }
}
