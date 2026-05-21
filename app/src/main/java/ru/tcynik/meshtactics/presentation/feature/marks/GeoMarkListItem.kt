package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkListItemUiModel

@Composable
fun GeoMarkListItem(
    item: GeoMarkListItemUiModel,
    onVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeIcon(
            shape = item.shape,
            colorArgb = item.colorArgb,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                TypeBadge(type = item.type)
            }
            Text(
                text = "${item.ttlLabel}  •  ${item.authorLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = item.isVisible,
            onCheckedChange = { checked -> onVisibilityToggle(item.id, checked) },
        )
        IconButton(onClick = { /* TODO: контекстное меню */ }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShapeIcon(
    shape: GeoMarkShape,
    colorArgb: Int,
    modifier: Modifier = Modifier,
) {
    val color = Color(colorArgb)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (shape) {
            GeoMarkShape.CIRCLE -> drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Fill)
            GeoMarkShape.SQUARE -> drawRect(color = color, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Fill)
            GeoMarkShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy + r)
                    lineTo(cx - r, cy + r)
                    close()
                }
                drawPath(path = path, color = color, style = Fill)
            }
        }
    }
}

@Composable
private fun TypeBadge(type: GeoMarkType) {
    val label = when (type) {
        GeoMarkType.POINT -> "точка"
        GeoMarkType.TRACK -> "трек"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
