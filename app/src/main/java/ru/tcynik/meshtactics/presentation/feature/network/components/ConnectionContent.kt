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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.presentation.feature.network.state.BleDeviceUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkConnectionState

@Composable
fun ConnectionContent(
    state: NetworkConnectionState,
    connectionStatus: MeshConnectionStatusUi,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus is MeshConnectionStatusUi.Connected
    var isExpanded by rememberSaveable { mutableStateOf(!isConnected) }
    var wasConnected by rememberSaveable { mutableStateOf(isConnected) }

    LaunchedEffect(isConnected) {
        if (!wasConnected && isConnected) {
            isExpanded = false
        }
        wasConnected = isConnected
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.network_connection_title_scanning),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) {
                        stringResource(R.string.network_action_collapse)
                    } else {
                        stringResource(R.string.network_action_expand)
                    },
                )
            }

            if (!isExpanded) return@Column

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isScanning) {
                    OutlinedButton(
                        onClick = onStopScanClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.network_connection_stop_scan))
                    }
                } else {
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier.weight(1f),
                        enabled = connectionStatus is MeshConnectionStatusUi.Disconnected
                                || connectionStatus is MeshConnectionStatusUi.Scanning
                                || connectionStatus is MeshConnectionStatusUi.Connected
                                || connectionStatus is MeshConnectionStatusUi.Error,
                    ) {
                        Text(stringResource(R.string.network_connection_start_scan))
                    }
                }
            }

            if (state.isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.scannedDevices.isEmpty() && !state.isScanning) {
                Text(
                    text = stringResource(R.string.network_connection_no_nodes_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.network_connection_nodes_found, state.scannedDevices.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.scannedDevices.forEach { device ->
                        BleDeviceRow(
                            device = device,
                            onConnectClick = { onConnectClick(device.address) },
                            isConnecting = state.connectingAddress == device.address,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BleDeviceRow(
    device: BleDeviceUi,
    onConnectClick: () -> Unit,
    isConnecting: Boolean,
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
                Text(
                    text = device.name.ifBlank { stringResource(R.string.network_connection_unknown_device) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.network_connection_rssi, device.rssi),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            OutlinedButton(
                onClick = onConnectClick,
                enabled = !isConnecting,
            ) {
                Text(
                    if (isConnecting) {
                        stringResource(R.string.network_connection_connecting)
                    } else {
                        stringResource(R.string.network_connection_connect)
                    }
                )
            }
        }
    }
}
