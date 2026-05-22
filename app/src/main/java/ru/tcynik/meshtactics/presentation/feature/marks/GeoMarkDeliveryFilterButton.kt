package ru.tcynik.meshtactics.presentation.feature.marks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterButtonUi
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus

@Composable
fun GeoMarkDeliveryFilterButton(
    filter: GeoMarkDeliveryFilterButtonUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (imageVector, contentDescription) = geoMarkDeliveryIconSpec(filter.deliveryState)
    val enabled = filter.status != GeoMarkDeliveryFilterStatus.INACTIVE
    val tint = when (filter.status) {
        GeoMarkDeliveryFilterStatus.INACTIVE ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        GeoMarkDeliveryFilterStatus.SELECTED ->
            MaterialTheme.colorScheme.primary
        GeoMarkDeliveryFilterStatus.UNSELECTED ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (filter.status == GeoMarkDeliveryFilterStatus.SELECTED) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                )
            }
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(22.dp)
                    .alpha(if (enabled) 1f else 0.38f),
                tint = tint,
            )
        }
    }
}
