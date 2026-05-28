package ru.tcynik.meshtactics.presentation.feature.network.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.network.state.DeviceMetricsUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshNodeUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkTelemetryState

@Composable
fun TelemetryContent(
    state: NetworkTelemetryState,
    connectionStatus: MeshConnectionStatusUi,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus is MeshConnectionStatusUi.Connected
    var isTelemetryExpanded by rememberSaveable { mutableStateOf(false) }
    var isNodesExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onRefreshClick,
            enabled = isConnected && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Обновить")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTelemetryExpanded = !isTelemetryExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Телеметрия",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isTelemetryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isTelemetryExpanded) "Свернуть" else "Развернуть",
                    )
                }
                if (isTelemetryExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceMetricsCard(metrics = state.deviceMetrics)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNodesExpanded = !isNodesExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ноды (${state.meshNodes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isNodesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isNodesExpanded) "Свернуть" else "Развернуть",
                    )
                }
                if (isNodesExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.meshNodes.isEmpty()) {
                        Text(
                            text = "No nodes received yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.meshNodes.forEach { node ->
                                MeshNodeRow(node = node)
                            }
                        }
                    }
                }
            }
        }
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
