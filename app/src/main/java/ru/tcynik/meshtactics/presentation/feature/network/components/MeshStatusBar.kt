package ru.tcynik.meshtactics.presentation.feature.network.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi

@Composable
fun MeshStatusBar(
    status: MeshConnectionStatusUi,
    rebootingNodeName: String,
    onDisconnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
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
                        text = if (status is MeshConnectionStatusUi.Connecting) "Cancel" else "Disconnect",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (status is MeshConnectionStatusUi.Connected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки ноды",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: MeshConnectionStatusUi) {
    val color = when (status) {
        is MeshConnectionStatusUi.Connected -> Color(0xFF4CAF50)
        is MeshConnectionStatusUi.Connecting -> Color(0xFFFFC107)
        is MeshConnectionStatusUi.Rebooting -> Color(0xFFFFC107)
        is MeshConnectionStatusUi.Scanning -> Color(0xFF2196F3)
        is MeshConnectionStatusUi.Error -> MaterialTheme.colorScheme.error
        is MeshConnectionStatusUi.Disconnected -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

private fun statusLabel(status: MeshConnectionStatusUi, rebootingNodeName: String): String = when (status) {
    is MeshConnectionStatusUi.Disconnected -> "Not connected"
    is MeshConnectionStatusUi.Scanning -> "Scanning..."
    is MeshConnectionStatusUi.Rebooting ->
        if (rebootingNodeName.isNotBlank()) "$rebootingNodeName - Перезагрузка..."
        else "Перезагрузка..."
    is MeshConnectionStatusUi.Connecting -> "Connecting to ${status.deviceName}..."
    is MeshConnectionStatusUi.Connected -> rebootingNodeName.ifBlank { status.nodeId }
    is MeshConnectionStatusUi.Error -> "Error: ${status.message}"
}
