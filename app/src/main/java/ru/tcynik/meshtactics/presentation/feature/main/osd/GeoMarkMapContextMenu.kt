package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarkCreatedAtFormatter
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarkShapeIcon
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarkTitleFormatter
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarkTrackEndIcon

@Composable
fun GeoMarkMapContextMenu(
    screenXDp: Float,
    screenYDp: Float,
    title: String?,
    mark: GeoMarkModel? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val offsetPx = with(density) {
        IntOffset(screenXDp.dp.roundToPx(), screenYDp.dp.roundToPx())
    }
    Popup(
        alignment = androidx.compose.ui.Alignment.TopStart,
        offset = offsetPx,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val bottomShape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
        val fullShape = RoundedCornerShape(4.dp)
        val containerColor = MaterialTheme.colorScheme.surfaceContainer

        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(max = 280.dp),
        ) {
            if (title != null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                    color = containerColor,
                    shadowElevation = 3.dp,
                ) {
                    GeoMarkMapContextMenuHeader(title = title, mark = mark)
                }
            }
            Surface(
                shape = if (title != null) bottomShape else fullShape,
                color = containerColor,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(vertical = 4.dp), content = content)
            }
        }
    }
}

@Composable
private fun GeoMarkMapContextMenuHeader(
    title: String,
    mark: GeoMarkModel?,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mark != null) {
            val colorArgb = GeoMarkColor.colorAt(mark.color)
            val iconModifier = Modifier
                .padding(end = 8.dp)
                .size(24.dp)
            if (mark.type == GeoMarkType.TRACK) {
                GeoMarkTrackEndIcon(
                    endType = mark.trackEndType,
                    colorArgb = colorArgb,
                    modifier = iconModifier,
                )
            } else {
                GeoMarkShapeIcon(
                    shape = mark.shape,
                    colorArgb = colorArgb,
                    modifier = iconModifier,
                )
            }
        }
        if (mark != null) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val name = mark.name.ifBlank { "—" }
            val author = GeoMarkTitleFormatter.authorLabel(mark)
            val createdAtLabel = GeoMarkCreatedAtFormatter.format(mark.createdAt, nowSeconds)
            Text(
                text = "$name от $author",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = createdAtLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GeoMarkMapContextMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
