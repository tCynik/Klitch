package ru.tcynik.meshtactics.presentation.feature.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.presentation.feature.network.components.NetworkSettingsContent
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = when (val connectionStatus = state.connectionStatus) {
        is MeshConnectionStatusUi.Connected -> {
            connectionStatus.deviceName.ifBlank { "Настройки ноды" }
        }
        else -> "Настройки ноды"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        NetworkSettingsContent(
            state = state.settings,
            connectionStatus = state.connectionStatus,
            onReadConfigClick = viewModel::onReadConfigClick,
            onEditConfigClick = viewModel::onEditConfigClick,
            onWriteConfigClick = viewModel::onWriteConfigClick,
            onLongNameChange = viewModel::onConfigLongNameChange,
            onShortNameChange = viewModel::onConfigShortNameChange,
            onChannelNameChange = viewModel::onChannelNameChange,
            onChannelPskChange = viewModel::onChannelPskChange,
            onAddChannelClick = viewModel::onAddChannelClick,
            onProvideLocationToggle = viewModel::onProvideLocationToggle,
            onGpsModeChange = viewModel::onGpsModeChange,
            onRemoveFixedPosition = viewModel::onRemoveFixedPosition,
            onBroadcastIntervalChange = viewModel::onBroadcastIntervalChange,
            onSmartBroadcastToggle = viewModel::onSmartBroadcastToggle,
            onPositionFlagsChange = viewModel::onPositionFlagsChange,
            onChannelPositionPrecisionChange = viewModel::onChannelPositionPrecisionChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
