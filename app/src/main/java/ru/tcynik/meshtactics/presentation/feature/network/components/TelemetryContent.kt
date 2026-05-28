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

private const val TITLE_TELEMETRY = "Телеметрия"
private const val ACTION_COLLAPSE = "Свернуть"
private const val ACTION_EXPAND = "Развернуть"
private const val REFRESH_BUTTON_TEXT = "Обновить"
private const val NODES_TITLE_TEMPLATE = "Ноды (%d)"
private const val NO_NODES_TEXT = "No nodes received yet."
private const val NO_DATA_TEXT = "No data yet."
private const val NO_VALUE_PLACEHOLDER = "—"
private const val METRIC_BATTERY = "Battery"
private const val METRIC_VOLTAGE = "Voltage"
private const val METRIC_CHANNEL_UTILIZATION = "Chan utilization"
private const val METRIC_AIR_UTIL_TX = "Air util TX"
private const val METRIC_UPTIME = "Uptime"
private const val BATTERY_TEMPLATE = "%d%%"
private const val SNR_TEMPLATE = "SNR %s"
private const val HOPS_TEMPLATE = "%d hop%s"

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
                        text = TITLE_TELEMETRY,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isTelemetryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isTelemetryExpanded) ACTION_COLLAPSE else ACTION_EXPAND,
                    )
                }
                if (isTelemetryExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceMetricsCard(metrics = state.deviceMetrics)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRefreshClick,
                        enabled = isConnected && !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(REFRESH_BUTTON_TEXT)
                    }
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
                        text = NODES_TITLE_TEMPLATE.format(state.meshNodes.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isNodesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isNodesExpanded) ACTION_COLLAPSE else ACTION_EXPAND,
                    )
                }
                if (isNodesExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.meshNodes.isEmpty()) {
                        Text(
                            text = NO_NODES_TEXT,
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
            if (metrics == null) {
                Text(
                    text = NO_DATA_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MetricRow(METRIC_BATTERY, metrics.batteryLevel?.let { BATTERY_TEMPLATE.format(it) } ?: NO_VALUE_PLACEHOLDER)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(METRIC_VOLTAGE, metrics.voltage ?: NO_VALUE_PLACEHOLDER)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(METRIC_CHANNEL_UTILIZATION, metrics.channelUtilization ?: NO_VALUE_PLACEHOLDER)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(METRIC_AIR_UTIL_TX, metrics.airUtilTx ?: NO_VALUE_PLACEHOLDER)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(METRIC_UPTIME, metrics.uptimeFormatted ?: NO_VALUE_PLACEHOLDER)
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
                    text = SNR_TEMPLATE.format(node.snr),
                    style = MaterialTheme.typography.labelSmall,
                )
                node.hopsAway?.let {
                    Text(
                        text = HOPS_TEMPLATE.format(it, if (it != 1) "s" else ""),
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
