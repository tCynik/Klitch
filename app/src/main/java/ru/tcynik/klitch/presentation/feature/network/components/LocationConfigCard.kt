package ru.tcynik.klitch.presentation.feature.network.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import ru.tcynik.klitch.presentation.feature.network.state.models.BlockReason
import ru.tcynik.klitch.presentation.feature.network.state.models.GpsModeUi
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationConfigOptions
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationConfigUi
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationSharingStatus

@Composable
fun LocationConfigCard(
    config: LocationConfigUi,
    isConnected: Boolean,
    useWakeLock: Boolean,
    onProvideLocationToggle: (Boolean) -> Unit,
    onGpsModeChange: (GpsModeUi) -> Unit,
    onRemoveFixedPosition: () -> Unit,
    onBroadcastIntervalChange: (Int) -> Unit,
    onSmartBroadcastToggle: (Boolean) -> Unit,
    onPositionFlagsChange: (Int) -> Unit,
    onChannelPositionPrecisionChange: (Int) -> Unit,
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

            // Section A: Phone → Node
            SectionHeader(stringResource(R.string.network_location_section_phone_to_node))
            SettingsRow(label = stringResource(R.string.network_location_provide_to_mesh)) {
                Switch(
                    checked = config.provideLocationToMesh,
                    onCheckedChange = { onProvideLocationToggle(it) },
                    enabled = isConnected,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsRow(label = stringResource(R.string.network_location_permission)) {
                Text(
                    text = if (config.hasLocationPermission) {
                        stringResource(R.string.network_location_permission_granted)
                    } else {
                        stringResource(R.string.network_location_permission_denied)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (config.hasLocationPermission)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section B: Node Position Source
            SectionHeader(stringResource(R.string.network_location_section_node_source))
            GpsModeRow(
                current = config.gpsMode,
                enabled = isConnected,
                onChanged = onGpsModeChange,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsRow(label = stringResource(R.string.network_location_fixed_position)) {
                if (config.fixedPositionEnabled) {
                    Button(
                        onClick = onRemoveFixedPosition,
                        enabled = isConnected,
                    ) {
                        Text(stringResource(R.string.network_location_remove))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.network_location_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section C: Node → Mesh Broadcast
            SectionHeader(stringResource(R.string.network_location_section_node_broadcast))
            BroadcastIntervalRow(
                current = config.broadcastIntervalSecs,
                enabled = isConnected,
                onChanged = onBroadcastIntervalChange,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SettingsRow(label = stringResource(R.string.network_location_smart_broadcast)) {
                Switch(
                    checked = config.smartBroadcastEnabled,
                    onCheckedChange = { onSmartBroadcastToggle(it) },
                    enabled = isConnected,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section D: Position Payload Flags
            SectionHeader(stringResource(R.string.network_location_section_flags))
            PositionFlagsSection(
                flags = config.positionFlags,
                enabled = isConnected,
                onFlagsChange = onPositionFlagsChange,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section E: Channel Position Precision
            SectionHeader(stringResource(R.string.network_location_section_precision))
            PrecisionRow(
                current = config.primaryChannelPositionPrecision,
                enabled = isConnected,
                onChanged = onChannelPositionPrecisionChange,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section F: Background stability
            SectionHeader("Фоновая стабильность")
            SettingsRow(label = "Wake lock (удерживать CPU в фоне)") {
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
    BlockReason.GPS_MODE_CONFLICT -> context.getString(R.string.network_location_reason_gps_conflict)
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
private fun GpsModeRow(
    current: GpsModeUi,
    enabled: Boolean,
    onChanged: (GpsModeUi) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.network_location_gps_mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        GpsModeUi.entries.forEach { mode ->
            val selected = mode == current
            Surface(
                onClick = { if (enabled) onChanged(mode) },
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PositionFlagsSection(
    flags: Int,
    enabled: Boolean,
    onFlagsChange: (Int) -> Unit,
) {
    val flagLabels = listOf(
        stringResource(R.string.network_flag_altitude),
        stringResource(R.string.network_flag_msl_altitude),
        stringResource(R.string.network_flag_sats_in_view),
        stringResource(R.string.network_flag_seq_no),
        stringResource(R.string.network_flag_timestamp),
        stringResource(R.string.network_flag_heading),
        stringResource(R.string.network_flag_speed),
    )
    val flagDefs = LocationConfigOptions.positionFlagBits.zip(flagLabels)
    Column {
        flagDefs.forEach { (bit, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = (flags and bit) != 0,
                    onCheckedChange = { checked ->
                        val newFlags = if (checked) flags or bit else flags and bit.inv()
                        onFlagsChange(newFlags)
                    },
                    enabled = enabled,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BroadcastIntervalRow(
    current: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    val intervalLabels = listOf(
        stringResource(R.string.network_interval_live),
        stringResource(R.string.network_interval_30s),
        stringResource(R.string.network_interval_1m),
        stringResource(R.string.network_interval_2m),
        stringResource(R.string.network_interval_5m),
        stringResource(R.string.network_interval_10m),
        stringResource(R.string.network_interval_15m),
        stringResource(R.string.network_interval_30m),
    )
    val options = LocationConfigOptions.broadcastIntervalSecs.zip(intervalLabels)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (value, label) ->
                    val selected = value == current
                    Surface(
                        onClick = { if (enabled) onChanged(value) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionRow(
    current: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    val precisionLabels = listOf(
        stringResource(R.string.network_precision_off),
        stringResource(R.string.network_precision_11km),
        stringResource(R.string.network_precision_14km),
        stringResource(R.string.network_precision_170m),
        stringResource(R.string.network_precision_21m),
        stringResource(R.string.network_precision_full),
    )
    val options = LocationConfigOptions.channelPrecisionBits.zip(precisionLabels)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (value, label) ->
                    val selected = value == current
                    Surface(
                        onClick = { if (enabled) onChanged(value) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
