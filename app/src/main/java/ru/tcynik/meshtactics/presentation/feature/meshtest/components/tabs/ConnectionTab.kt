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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.BleDeviceUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.ConnectionTabState
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshConnectionStatusUi

@Composable
fun ConnectionTab(
    state: ConnectionTabState,
    connectionStatus: MeshConnectionStatusUi,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isScanning) {
                OutlinedButton(
                    onClick = onStopScanClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stop Scan")
                }
            } else {
                Button(
                    onClick = onScanClick,
                    modifier = Modifier.weight(1f),
                    enabled = connectionStatus is MeshConnectionStatusUi.Disconnected
                            || connectionStatus is MeshConnectionStatusUi.Scanning
                            || connectionStatus is MeshConnectionStatusUi.Error,
                ) {
                    Text("Scan for devices")
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
                text = "No devices found. Press Scan to search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Found devices (${state.scannedDevices.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.scannedDevices, key = { it.address }) { device ->
                    BleDeviceRow(
                        device = device,
                        onConnectClick = { onConnectClick(device.address) },
                        isConnecting = connectionStatus is MeshConnectionStatusUi.Connecting
                                && connectionStatus.deviceName == device.name,
                    )
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
                    text = device.name.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            OutlinedButton(
                onClick = onConnectClick,
                enabled = !isConnecting,
            ) {
                Text(if (isConnecting) "Connecting..." else "Connect")
            }
        }
    }
}
