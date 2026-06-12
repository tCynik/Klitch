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
import ru.tcynik.klitch.presentation.feature.marks.models.GeoMarkDeliveryState

fun geoMarkDeliveryIconSpec(state: GeoMarkDeliveryState): Pair<ImageVector, String> = when (state) {
    GeoMarkDeliveryState.LOCAL -> Icons.Outlined.Save to "Сохранено в базу"
    GeoMarkDeliveryState.QUEUED -> Icons.Outlined.Schedule to "В очереди на отправку"
    GeoMarkDeliveryState.SENT -> Icons.Outlined.Email to "Отправлено"
    GeoMarkDeliveryState.RECEIVED -> Icons.Outlined.MoveToInbox to "Принято из сети"
}

@Composable
fun GeoMarkDeliveryIcon(
    state: GeoMarkDeliveryState,
    modifier: Modifier = Modifier,
) {
    val (imageVector, contentDescription) = geoMarkDeliveryIconSpec(state)
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
