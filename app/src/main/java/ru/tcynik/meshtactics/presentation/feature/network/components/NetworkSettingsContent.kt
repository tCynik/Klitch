package ru.tcynik.meshtactics.presentation.feature.network.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.network.state.ChannelConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.DeviceConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkSettingsState
import ru.tcynik.meshtactics.presentation.feature.network.state.models.GpsModeUi

@Composable
fun NetworkSettingsContent(
    state: NetworkSettingsState,
    connectionStatus: MeshConnectionStatusUi,
    onReadConfigClick: () -> Unit,
    onEditConfigClick: () -> Unit,
    onWriteConfigClick: () -> Unit,
    onLongNameChange: (String) -> Unit = {},
    onShortNameChange: (String) -> Unit = {},
    onChannelNameChange: (index: Int, value: String) -> Unit = { _, _ -> },
    onChannelPskChange: (index: Int, value: String) -> Unit = { _, _ -> },
    onAddChannelClick: () -> Unit = {},
    onProvideLocationToggle: (Boolean) -> Unit = {},
    onGpsModeChange: (GpsModeUi) -> Unit = {},
    onRemoveFixedPosition: () -> Unit = {},
    onBroadcastIntervalChange: (Int) -> Unit = {},
    onSmartBroadcastToggle: (Boolean) -> Unit = {},
    onPositionFlagsChange: (Int) -> Unit = {},
    onChannelPositionPrecisionChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionStatus is MeshConnectionStatusUi.Connected

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onReadConfigClick,
                enabled = isConnected && !state.isLoading,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Read")
            }
            if (state.isEditing) {
                Button(
                    onClick = onWriteConfigClick,
                    enabled = isConnected && !state.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Write")
                }
            } else {
                OutlinedButton(
                    onClick = onEditConfigClick,
                    enabled = isConnected && state.deviceConfig != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.deviceConfig?.let { config ->
                DeviceConfigCard(
                    config = config,
                    isEditing = state.isEditing,
                    onLongNameChange = onLongNameChange,
                    onShortNameChange = onShortNameChange,
                )
            } ?: run {
                if (!state.isLoading) {
                    Text(
                        text = "No config loaded. Press Read to fetch from device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.channels.forEach { channel ->
                ChannelConfigCard(
                    config = channel,
                    isEditing = state.isEditing,
                    onNameChange = { onChannelNameChange(channel.index, it) },
                    onPskChange = { onChannelPskChange(channel.index, it) },
                )
            }

            if (state.isEditing && state.channels.size < 8) {
                OutlinedButton(
                    onClick = onAddChannelClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ Add Channel")
                }
            }

            state.locationConfig?.let { locationConfig ->
                LocationConfigCard(
                    config = locationConfig,
                    isConnected = isConnected,
                    onProvideLocationToggle = onProvideLocationToggle,
                    onGpsModeChange = onGpsModeChange,
                    onRemoveFixedPosition = onRemoveFixedPosition,
                    onBroadcastIntervalChange = onBroadcastIntervalChange,
                    onSmartBroadcastToggle = onSmartBroadcastToggle,
                    onPositionFlagsChange = onPositionFlagsChange,
                    onChannelPositionPrecisionChange = onChannelPositionPrecisionChange,
                )
            }
        }
    }
}

@Composable
private fun DeviceConfigCard(
    config: DeviceConfigUi,
    isEditing: Boolean,
    onLongNameChange: (String) -> Unit,
    onShortNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device Config",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConfigRow(label = "Long name", value = config.longName, isEditing = isEditing, onValueChange = onLongNameChange)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ConfigRow(label = "Short name", value = config.shortName, isEditing = isEditing, onValueChange = onShortNameChange)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ConfigRow(label = "LoRa preset", value = config.loraPreset, isEditing = false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ConfigRow(label = "TX power", value = config.txPowerDbm, isEditing = false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ConfigRow(label = "Region", value = config.region, isEditing = false)
        }
    }
}

@Composable
private fun ChannelConfigCard(
    config: ChannelConfigUi,
    isEditing: Boolean,
    onNameChange: (String) -> Unit,
    onPskChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Channel ${config.index}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConfigRow(
                label = "Name",
                value = config.channelName,
                isEditing = isEditing,
                onValueChange = onNameChange,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = config.pskBase64,
                    onValueChange = onPskChange,
                    label = { Text("PSK (Base64)") },
                    placeholder = { Text("empty = no encryption") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = config.pskError != null,
                    supportingText = config.pskError?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    },
                )
            } else {
                ConfigRow(
                    label = "PSK",
                    value = if (config.pskBase64.isBlank()) "none" else "••••••••",
                    isEditing = false,
                )
            }
        }
    }
}

@Composable
private fun ConfigRow(
    label: String,
    value: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isEditing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
        )
    } else {
        Row(
            modifier = modifier
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
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
