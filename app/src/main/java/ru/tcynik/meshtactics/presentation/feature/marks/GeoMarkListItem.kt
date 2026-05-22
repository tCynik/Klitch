package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkListItemUiModel

@Composable
fun GeoMarkListItem(
    item: GeoMarkListItemUiModel,
    onVisibilityToggle: (id: String, visible: Boolean) -> Unit,
    onMenuDelete: () -> Unit,
    onMenuExtend: () -> Unit,
    onMenuSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isVisible,
            onCheckedChange = { checked -> onVisibilityToggle(item.id, checked) },
        )
        if (item.type == GeoMarkType.TRACK) {
            GeoMarkTrackEndIcon(
                endType = item.trackEndType,
                colorArgb = item.colorArgb,
                modifier = Modifier.size(32.dp),
            )
        } else {
            GeoMarkShapeIcon(
                shape = item.shape,
                colorArgb = item.colorArgb,
                modifier = Modifier.size(32.dp),
            )
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DeliveryStateIcon(state = item.deliveryState)
                Text(
                    text = "${item.ttlLabel}  •  ${item.authorLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "Меню",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    onClick = {
                        menuExpanded = false
                        onMenuDelete()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Продлить") },
                    onClick = {
                        menuExpanded = false
                        onMenuExtend()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Отправить") },
                    onClick = {
                        menuExpanded = false
                        onMenuSend()
                    },
                )
            }
        }
    }
}

@Composable
private fun DeliveryStateIcon(state: GeoMarkDeliveryState, modifier: Modifier = Modifier) {
    GeoMarkDeliveryIcon(state = state, modifier = modifier.size(16.dp))
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
