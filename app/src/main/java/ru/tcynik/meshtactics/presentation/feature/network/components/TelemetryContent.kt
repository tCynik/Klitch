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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.R
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
                        text = stringResource(R.string.network_telemetry_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isTelemetryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isTelemetryExpanded) {
                            stringResource(R.string.network_action_collapse)
                        } else {
                            stringResource(R.string.network_action_expand)
                        },
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
                        Text(stringResource(R.string.network_telemetry_refresh))
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
                        text = stringResource(R.string.network_telemetry_nodes_title, state.meshNodes.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isNodesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isNodesExpanded) {
                            stringResource(R.string.network_action_collapse)
                        } else {
                            stringResource(R.string.network_action_expand)
                        },
                    )
                }
                if (isNodesExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.meshNodes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.network_telemetry_no_nodes),
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
                    text = stringResource(R.string.network_telemetry_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MetricRow(
                    stringResource(R.string.network_metric_battery),
                    metrics.batteryLevel?.let {
                        stringResource(R.string.network_metric_battery_percent, it)
                    } ?: stringResource(R.string.network_value_placeholder),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(
                    stringResource(R.string.network_metric_voltage),
                    metrics.voltage ?: stringResource(R.string.network_value_placeholder),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(
                    stringResource(R.string.network_metric_channel_utilization),
                    metrics.channelUtilization ?: stringResource(R.string.network_value_placeholder),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(
                    stringResource(R.string.network_metric_air_util_tx),
                    metrics.airUtilTx ?: stringResource(R.string.network_value_placeholder),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(
                    stringResource(R.string.network_metric_uptime),
                    metrics.uptimeFormatted ?: stringResource(R.string.network_value_placeholder),
                )
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
                    text = stringResource(R.string.network_node_snr, node.snr),
                    style = MaterialTheme.typography.labelSmall,
                )
                node.hopsAway?.let {
                    Text(
                        text = stringResource(
                            R.string.network_node_hops,
                            it,
                            if (it != 1) stringResource(R.string.network_node_hops_suffix_plural) else "",
                        ),
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
