package ru.tcynik.meshtactics.presentation.feature.meshtest.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.DeviceMetricsUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshNodeUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.TelemetryTabState

@Composable
fun TelemetryTab(
    state: TelemetryTabState,
    connectionStatus: MeshConnectionStatusUi,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus is MeshConnectionStatusUi.Connected

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(
                onClick = onRefreshClick,
                enabled = isConnected && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh telemetry")
            }
        }

        item {
            DeviceMetricsCard(metrics = state.deviceMetrics)
        }

        item {
            Text(
                text = "Mesh nodes (${state.meshNodes.size})",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (state.meshNodes.isEmpty()) {
            item {
                Text(
                    text = "No nodes received yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.meshNodes, key = { it.nodeId }) { node ->
                MeshNodeRow(node = node)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun DeviceMetricsCard(
    metrics: DeviceMetricsUi?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device Metrics",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (metrics == null) {
                Text(
                    text = "No data yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MetricRow("Battery", metrics.batteryLevel?.let { "$it%" } ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow("Voltage", metrics.voltage ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow("Chan utilization", metrics.channelUtilization ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow("Air util TX", metrics.airUtilTx ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow("Uptime", metrics.uptimeFormatted ?: "—")
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MeshNodeRow(
    node: MeshNodeUi,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = node.shortName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = node.longName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = node.nodeId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SNR ${node.snr}",
                    style = MaterialTheme.typography.labelSmall,
                )
                node.hopsAway?.let {
                    Text(
                        text = "$it hop${if (it != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = node.lastHeardFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
