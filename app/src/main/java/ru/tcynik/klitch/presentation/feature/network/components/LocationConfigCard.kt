package ru.tcynik.klitch.presentation.feature.network.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.R
import ru.tcynik.klitch.mesh.service.AndroidMeshLocationManager
import ru.tcynik.klitch.presentation.feature.network.state.models.BlockReason
import ru.tcynik.klitch.presentation.feature.network.state.models.GpsModeUi
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationConfigOptions
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationConfigUi
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationSharingStatus

/**
 * Узел сам настраивается под приложение (см. NodeProvisioningUseCase) — юзеру не нужно
 * разбираться в протоколе Meshtastic. Карточка показывает текущее состояние как информацию.
 */
@Composable
fun LocationConfigCard(
    config: LocationConfigUi,
    useWakeLock: Boolean,
    onWakeLockToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.network_location_config_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))

            ReadinessBanner(status = config.sharingStatus)

            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(stringResource(R.string.network_location_section_phone_to_node))
            if (config.gpsMode == GpsModeUi.ENABLED) {
                Text(
                    text = stringResource(R.string.network_location_node_gps_active_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                InfoRow(
                    label = stringResource(R.string.network_location_provide_to_mesh),
                    value = stringResource(
                        if (config.provideLocationToMesh) R.string.network_location_status_enabled
                        else R.string.network_location_status_disabled
                    ),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow(
                    label = stringResource(R.string.network_location_permission),
                    value = if (config.hasLocationPermission) {
                        stringResource(R.string.network_location_permission_granted)
                    } else {
                        stringResource(R.string.network_location_permission_denied)
                    },
                    valueColor = if (config.hasLocationPermission)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.network_location_section_node_source))
            InfoRow(label = stringResource(R.string.network_location_gps_mode), value = config.gpsMode.name)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(
                label = stringResource(R.string.network_location_fixed_position),
                value = if (config.fixedPositionEnabled) {
                    stringResource(R.string.network_location_reason_fixed_active)
                } else {
                    stringResource(R.string.network_location_not_set)
                },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.network_location_section_node_broadcast))
            InfoRow(
                label = stringResource(R.string.network_location_section_node_broadcast),
                value = broadcastIntervalLabel(config.broadcastIntervalSecs),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(
                label = stringResource(R.string.network_location_smart_broadcast),
                value = stringResource(
                    if (config.smartBroadcastEnabled) R.string.network_location_status_enabled
                    else R.string.network_location_status_disabled
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.network_location_section_flags))
            Text(
                text = positionFlagsLabel(config.positionFlags),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.network_location_section_precision))
            InfoRow(
                label = stringResource(R.string.network_location_section_precision),
                value = channelPrecisionLabel(config.primaryChannelPositionPrecision),
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(stringResource(R.string.location_config_bg_stability_section))
            SettingsRow(label = stringResource(R.string.node_settings_wake_lock)) {
                Switch(
                    checked = useWakeLock,
                    onCheckedChange = onWakeLockToggle,
                )
            }
        }
    }
}

@Composable
private fun ReadinessBanner(status: LocationSharingStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backgroundColor = when (status) {
        is LocationSharingStatus.Ready -> MaterialTheme.colorScheme.tertiary
        is LocationSharingStatus.Warning -> MaterialTheme.colorScheme.secondary
        is LocationSharingStatus.Blocked -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (status) {
        is LocationSharingStatus.Ready -> MaterialTheme.colorScheme.onTertiary
        is LocationSharingStatus.Warning -> MaterialTheme.colorScheme.onSecondary
        is LocationSharingStatus.Blocked -> MaterialTheme.colorScheme.onError
    }
    val label = when (status) {
        is LocationSharingStatus.Ready -> stringResource(R.string.network_location_banner_ready)
        is LocationSharingStatus.Warning -> stringResource(R.string.network_location_banner_warning)
        is LocationSharingStatus.Blocked -> stringResource(R.string.network_location_banner_blocked)
    }
    val details = when (status) {
        is LocationSharingStatus.Ready -> stringResource(R.string.network_location_banner_ready_details)
        is LocationSharingStatus.Warning -> status.reasons.joinToString(" · ") { it.toLabel(context) }
        is LocationSharingStatus.Blocked -> status.reasons.joinToString(" · ") { it.toLabel(context) }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun BlockReason.toLabel(context: Context): String = when (this) {
    BlockReason.PROVIDE_LOCATION_DISABLED -> context.getString(R.string.network_location_reason_sharing_disabled)
    BlockReason.LOCATION_PERMISSION_DENIED -> context.getString(R.string.network_location_reason_permission_denied)
    BlockReason.FIXED_POSITION_ACTIVE -> context.getString(R.string.network_location_reason_fixed_active)
    BlockReason.CHANNEL_PRECISION_DISABLED -> context.getString(R.string.network_location_reason_precision_zero)
    BlockReason.NO_POSITION_FLAGS -> context.getString(R.string.network_location_reason_no_flags)
    BlockReason.BROADCAST_INTERVAL_HIGH -> context.getString(R.string.network_location_reason_high_interval)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun broadcastIntervalLabel(secs: Int): String {
    if (secs == Int.MAX_VALUE) {
        val idleSecs = AndroidMeshLocationManager.STATIONARY_INTERVAL_MS / 1000
        val movingMinSecs = AndroidMeshLocationManager.MOBILE_INTERVAL_MS / 1000
        return stringResource(R.string.network_interval_app_driven, idleSecs, movingMinSecs)
    }
    val labels = listOf(
        stringResource(R.string.network_interval_live),
        stringResource(R.string.network_interval_30s),
        stringResource(R.string.network_interval_1m),
        stringResource(R.string.network_interval_2m),
        stringResource(R.string.network_interval_5m),
        stringResource(R.string.network_interval_10m),
        stringResource(R.string.network_interval_15m),
        stringResource(R.string.network_interval_30m),
    )
    val options = LocationConfigOptions.broadcastIntervalSecs.zip(labels)
    return options.firstOrNull { it.first == secs }?.second ?: "${secs}s"
}

@Composable
private fun channelPrecisionLabel(precision: Int): String {
    val labels = listOf(
        stringResource(R.string.network_precision_off),
        stringResource(R.string.network_precision_11km),
        stringResource(R.string.network_precision_14km),
        stringResource(R.string.network_precision_170m),
        stringResource(R.string.network_precision_21m),
        stringResource(R.string.network_precision_full),
    )
    val options = LocationConfigOptions.channelPrecisionBits.zip(labels)
    return options.firstOrNull { it.first == precision }?.second ?: precision.toString()
}

@Composable
private fun positionFlagsLabel(flags: Int): String {
    val labels = listOf(
        stringResource(R.string.network_flag_altitude),
        stringResource(R.string.network_flag_msl_altitude),
        stringResource(R.string.network_flag_sats_in_view),
        stringResource(R.string.network_flag_seq_no),
        stringResource(R.string.network_flag_timestamp),
        stringResource(R.string.network_flag_heading),
        stringResource(R.string.network_flag_speed),
    )
    val flagDefs = LocationConfigOptions.positionFlagBits.zip(labels)
    val enabled = flagDefs.filter { (bit, _) -> (flags and bit) != 0 }.map { it.second }
    return if (enabled.isEmpty()) {
        stringResource(R.string.network_location_reason_no_flags)
    } else {
        enabled.joinToString(", ")
    }
}
