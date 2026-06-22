package ru.tcynik.klitch.presentation.feature.network.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.tcynik.klitch.R
import ru.tcynik.klitch.mesh.ble.toMeshtasticDisplayShortName
import ru.tcynik.klitch.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.klitch.presentation.feature.network.state.models.GpsModeUi

@Composable
fun MeshStatusBar(
    status: MeshConnectionStatusUi,
    rebootingNodeName: String,
    hasNodeConfig: Boolean,
    gpsSourceMode: GpsModeUi?,
    onDisconnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGpsSourceToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSettingsIcon = status !is MeshConnectionStatusUi.Disconnected
            && status !is MeshConnectionStatusUi.Scanning
            && status !is MeshConnectionStatusUi.Error

    val isInProgress = status is MeshConnectionStatusUi.Connecting
            || status is MeshConnectionStatusUi.Syncing
            || status is MeshConnectionStatusUi.Rebooting
            || status is MeshConnectionStatusUi.WaitingForNode

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            StatusDot(status = status)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusLabel(status, rebootingNodeName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (status is MeshConnectionStatusUi.Connected || status is MeshConnectionStatusUi.Connecting) {
                if (status is MeshConnectionStatusUi.Connected) {
                    Text(
                        text = "RSSI ${status.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status.batteryLevel?.let { battery ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$battery%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Button(
                    onClick = onDisconnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.size(width = 100.dp, height = 32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (status is MeshConnectionStatusUi.Connecting) "Cancel" else stringResource(R.string.mesh_status_disconnect),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (showSettingsIcon) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSettingsClick,
                    enabled = status is MeshConnectionStatusUi.Connected && hasNodeConfig,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.mesh_status_cd_node_settings),
                    )
                }
            }
        }
            if (status is MeshConnectionStatusUi.Connected && gpsSourceMode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mesh_status_gps_source_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (gpsSourceMode == GpsModeUi.ENABLED)
                            stringResource(R.string.mesh_status_gps_source_node)
                        else
                            stringResource(R.string.mesh_status_gps_source_phone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = gpsSourceMode == GpsModeUi.ENABLED,
                        onCheckedChange = onGpsSourceToggle,
                    )
                }
            }
            if (isInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatusDot(status: MeshConnectionStatusUi) {
    val color = when (status) {
        is MeshConnectionStatusUi.Connected -> Color(0xFF4CAF50)
        is MeshConnectionStatusUi.Connecting -> Color(0xFFFFC107)
        is MeshConnectionStatusUi.Syncing -> Color(0xFFFFC107)
        is MeshConnectionStatusUi.Rebooting -> Color(0xFFFFC107)
        is MeshConnectionStatusUi.WaitingForNode -> Color(0xFF2196F3)
        is MeshConnectionStatusUi.Scanning -> Color(0xFF2196F3)
        is MeshConnectionStatusUi.Error -> MaterialTheme.colorScheme.error
        is MeshConnectionStatusUi.Disconnected -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun statusLabel(status: MeshConnectionStatusUi, rebootingNodeName: String): String = when (status) {
    is MeshConnectionStatusUi.Disconnected -> "Not connected"
    is MeshConnectionStatusUi.Scanning -> "Scanning..."
    is MeshConnectionStatusUi.Syncing ->
        nodeSyncLabel(rebootingNodeName, stringResource(R.string.mesh_status_syncing))
    is MeshConnectionStatusUi.Rebooting ->
        nodeSyncLabel(rebootingNodeName, stringResource(R.string.mesh_status_rebooting))
    is MeshConnectionStatusUi.WaitingForNode ->
        nodeSyncLabel(rebootingNodeName, stringResource(R.string.mesh_status_waiting))
    is MeshConnectionStatusUi.Connecting ->
        stringResource(R.string.mesh_status_connecting, status.deviceName.toMeshtasticDisplayShortName())
    is MeshConnectionStatusUi.Connected ->
        rebootingNodeName.toMeshtasticDisplayShortName()
            .ifBlank { status.deviceName.toMeshtasticDisplayShortName() }
            .ifBlank { status.nodeId }
    is MeshConnectionStatusUi.Error -> "Error: ${status.message}"
}

private fun nodeSyncLabel(nodeName: String, action: String): String =
    if (nodeName.isNotBlank()) {
        "${nodeName.toMeshtasticDisplayShortName()} - $action"
    } else {
        action
    }
