package ru.tcynik.klitch.presentation.feature.marks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryState

fun geoMarkDeliveryIconVector(state: GeoMarkDeliveryState): ImageVector = when (state) {
    GeoMarkDeliveryState.LOCAL -> Icons.Outlined.Save
    GeoMarkDeliveryState.QUEUED -> Icons.Outlined.Schedule
    GeoMarkDeliveryState.SENT -> Icons.Outlined.Email
    GeoMarkDeliveryState.RECEIVED -> Icons.Outlined.MoveToInbox
}

@Composable
fun GeoMarkDeliveryIcon(
    state: GeoMarkDeliveryState,
    modifier: Modifier = Modifier,
) {
    val contentDescription = when (state) {
        GeoMarkDeliveryState.LOCAL -> stringResource(R.string.geo_mark_delivery_local)
        GeoMarkDeliveryState.QUEUED -> stringResource(R.string.geo_mark_delivery_queued)
        GeoMarkDeliveryState.SENT -> stringResource(R.string.geo_mark_delivery_sent)
        GeoMarkDeliveryState.RECEIVED -> stringResource(R.string.geo_mark_delivery_received)
    }
    Icon(
        imageVector = geoMarkDeliveryIconVector(state),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
