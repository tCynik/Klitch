package ru.tcynik.meshtactics.presentation.feature.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.tcynik.meshtactics.presentation.feature.network.state.ChannelConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.DeviceConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkSettingsState
import ru.tcynik.meshtactics.presentation.feature.network.state.models.GpsModeUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsContent(
    state: NetworkSettingsState,
    connectionStatus: MeshConnectionStatusUi,
    onRefresh: () -> Unit,
    onSaveClick: () -> Unit,
    onLongNameChange: (String) -> Unit = {},
    onShortNameChange: (String) -> Unit = {},
    onChannelNameChange: (index: Int, value: String) -> Unit = { _, _ -> },
    onChannelPskChange: (index: Int, value: String) -> Unit = { _, _ -> },
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
    val hasPskErrors = state.channels.any { it.pskError != null }
    val canSave = isConnected && !state.isLoading && !hasPskErrors && state.shortNameError == null

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = if (state.hasChanges) 88.dp else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.deviceConfig != null) {
                item(key = "device_config") {
                    DeviceConfigCard(
                        config = state.deviceConfig,
                        shortNameError = state.shortNameError,
                        onLongNameChange = onLongNameChange,
                        onShortNameChange = onShortNameChange,
                    )
                }
            } else if (!state.isLoading) {
                item(key = "empty_state") {
                    Text(
                        text = "Нет данных. Подключитесь к ноде.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.channels.isNotEmpty()) {
                item(key = "channels") {
                    ChannelsCard(
                        channels = state.channels,
                        onNameChange = onChannelNameChange,
                        onPskChange = onChannelPskChange,
                    )
                }
            }

            state.locationConfig?.let { locationConfig ->
                item(key = "location_config") {
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

        AnimatedVisibility(
            visible = state.hasChanges,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Button(
                onClick = onSaveClick,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun DeviceConfigCard(
    config: DeviceConfigUi,
    shortNameError: String?,
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
            ConfigRow(label = "Long name", value = config.longName, isEditing = false)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            OutlinedTextField(
                value = config.shortName,
                onValueChange = onShortNameChange,
                label = { Text("Short name") },
                placeholder = { Text("1–4 символа") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = shortNameError != null,
                supportingText = shortNameError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error) }
                },
            )
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
private fun ChannelsCard(
    channels: List<ChannelConfigUi>,
    onNameChange: (index: Int, value: String) -> Unit,
    onPskChange: (index: Int, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Channels",
                style = MaterialTheme.typography.titleSmall,
            )
            channels.forEachIndexed { i, channel ->
                Spacer(modifier = Modifier.height(12.dp))
                if (i > 0) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    text = "Channel ${channel.index}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigRow(
                    label = "Name",
                    value = channel.channelName,
                    isEditing = true,
                    onValueChange = { onNameChange(channel.index, it) },
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = channel.pskBase64,
                    onValueChange = { onPskChange(channel.index, it) },
                    label = { Text("PSK (Base64)") },
                    placeholder = { Text("empty = no encryption") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = channel.pskError != null,
                    supportingText = channel.pskError?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    },
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
