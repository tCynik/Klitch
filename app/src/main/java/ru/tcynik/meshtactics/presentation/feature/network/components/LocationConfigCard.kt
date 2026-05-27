package ru.tcynik.meshtactics.presentation.feature.network.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.network.state.models.BlockReason
import ru.tcynik.meshtactics.presentation.feature.network.state.models.GpsModeUi
import ru.tcynik.meshtactics.presentation.feature.network.state.models.LocationConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.models.LocationSharingStatus

@Composable
fun LocationConfigCard(
    config: LocationConfigUi,
    isConnected: Boolean,
    onProvideLocationToggle: (Boolean) -> Unit,
    onGpsModeChange: (GpsModeUi) -> Unit,
    onRemoveFixedPosition: () -> Unit,
    onBroadcastIntervalChange: (Int) -> Unit,
    onSmartBroadcastToggle: (Boolean) -> Unit,
    onPositionFlagsChange: (Int) -> Unit,
    onChannelPositionPrecisionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Location Config", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            ReadinessBanner(status = config.sharingStatus)

            Spacer(modifier = Modifier.height(12.dp))

            // Section A: Phone → Node
            SectionHeader("Phone → Node")
            SettingsRow(label = "Provide location to mesh") {
                Switch(
                    checked = config.provideLocationToMesh,
                    onCheckedChange = { onProvideLocationToggle(it) },
                    enabled = isConnected,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsRow(label = "Location permission") {
                Text(
                    text = if (config.hasLocationPermission) "Granted" else "Denied",
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
            SectionHeader("Node Position Source")
            GpsModeRow(
                current = config.gpsMode,
                enabled = isConnected,
                onChanged = onGpsModeChange,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsRow(label = "Fixed position") {
                if (config.fixedPositionEnabled) {
                    Button(
                        onClick = onRemoveFixedPosition,
                        enabled = isConnected,
                    ) {
                        Text("Remove")
                    }
                } else {
                    Text(
                        text = "Not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section C: Node → Mesh Broadcast
            SectionHeader("Node → Mesh Broadcast")
            BroadcastIntervalRow(
                current = config.broadcastIntervalSecs,
                enabled = isConnected,
                onChanged = onBroadcastIntervalChange,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SettingsRow(label = "Smart broadcast") {
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
            SectionHeader("Position Flags")
            PositionFlagsSection(
                flags = config.positionFlags,
                enabled = isConnected,
                onFlagsChange = onPositionFlagsChange,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Section E: Channel Position Precision
            SectionHeader("Channel Precision (primary)")
            PrecisionRow(
                current = config.primaryChannelPositionPrecision,
                enabled = isConnected,
                onChanged = onChannelPositionPrecisionChange,
            )
        }
    }
}

@Composable
private fun ReadinessBanner(status: LocationSharingStatus, modifier: Modifier = Modifier) {
    val errorColor = MaterialTheme.colorScheme.error
    val backgroundColor = when (status) {
        is LocationSharingStatus.Ready -> Color(0xFF2E7D32)
        is LocationSharingStatus.Warning -> Color(0xFFF57F17)
        is LocationSharingStatus.Blocked -> errorColor
    }
    val label = when (status) {
        is LocationSharingStatus.Ready -> "Ready"
        is LocationSharingStatus.Warning -> "Warning"
        is LocationSharingStatus.Blocked -> "Blocked"
    }
    val details = when (status) {
        is LocationSharingStatus.Ready -> "Live GPS sharing is active"
        is LocationSharingStatus.Warning -> status.reasons.joinToString(" · ") { it.toLabel() }
        is LocationSharingStatus.Blocked -> status.reasons.joinToString(" · ") { it.toLabel() }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

private fun BlockReason.toLabel(): String = when (this) {
    BlockReason.PROVIDE_LOCATION_DISABLED -> "Sharing disabled"
    BlockReason.LOCATION_PERMISSION_DENIED -> "No location permission"
    BlockReason.FIXED_POSITION_ACTIVE -> "Fixed position active"
    BlockReason.CHANNEL_PRECISION_DISABLED -> "Channel precision = 0"
    BlockReason.NO_POSITION_FLAGS -> "No position flags"
    BlockReason.BROADCAST_INTERVAL_HIGH -> "High broadcast interval"
    BlockReason.GPS_MODE_CONFLICT -> "Node GPS may overwrite"
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
            text = "GPS mode",
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
    val flagDefs = listOf(
        1 to "Altitude",
        2 to "MSL Altitude",
        32 to "Sats in view",
        64 to "Seq no",
        128 to "Timestamp",
        256 to "Heading ⭐",
        512 to "Speed ⭐",
    )
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

private val BROADCAST_INTERVAL_OPTIONS = listOf(
    15   to "Live",
    30   to "30s",
    60   to "1 min",
    120  to "2 min",
    300  to "5 min",
    600  to "10 min",
    900  to "15 min",
    1800 to "30 min",
)

@Composable
private fun BroadcastIntervalRow(
    current: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BROADCAST_INTERVAL_OPTIONS.chunked(4).forEach { row ->
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

private val PRECISION_OPTIONS = listOf(
    0 to "Off",
    10 to "~11 km",
    13 to "~1.4 km",
    16 to "~170 m",
    19 to "~21 m",
    32 to "Full",
)

@Composable
private fun PrecisionRow(
    current: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PRECISION_OPTIONS.chunked(3).forEach { row ->
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
