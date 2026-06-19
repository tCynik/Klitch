package ru.tcynik.klitch.presentation.feature.network

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.tcynik.klitch.R
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.klitch.presentation.feature.network.components.NetworkSettingsContent
import ru.tcynik.klitch.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.klitch.presentation.ui.components.SyncRequiredDialog
import ru.tcynik.klitch.presentation.util.requestIgnoreBatteryOptimizationIfNeeded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shortName = state.settings.deviceConfig?.shortName.orEmpty()
    val title = stringResource(R.string.network_settings_title_prefix, shortName).trim()
    val context = LocalContext.current

    BackHandler(enabled = state.syncRequired) {
        viewModel.onNavigateBackRequested(onNavigateBack)
    }

    if (state.showLeaveSyncDialog) {
        SyncRequiredDialog(
            onConfirm = {
                viewModel.onDismissLeaveSyncDialog()
                viewModel.onSyncClick()
            },
            onDismiss = viewModel::onDismissLeaveSyncDialog,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onNavigateBackRequested(onNavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.network_settings_cd_back))
                    }
                },
            )
        },
    ) { padding ->
        NetworkSettingsContent(
            state = state.settings,
            connectionStatus = state.connectionStatus,
            syncRequired = state.syncRequired,
            onSyncClick = viewModel::onSyncClick,
            onRefresh = viewModel::onReadConfigClick,
            onSaveClick = viewModel::onWriteConfigClick,
            onLongNameChange = viewModel::onConfigLongNameChange,
            onShortNameChange = viewModel::onConfigShortNameChange,
            onChannelNameChange = viewModel::onChannelNameChange,
            onChannelPskChange = viewModel::onChannelPskChange,
            onProvideLocationToggle = { enabled ->
                viewModel.onProvideLocationToggle(enabled)
                if (enabled) context.requestIgnoreBatteryOptimizationIfNeeded()
            },
            onGpsModeChange = viewModel::onGpsModeChange,
            onRemoveFixedPosition = viewModel::onRemoveFixedPosition,
            onBroadcastIntervalChange = viewModel::onBroadcastIntervalChange,
            onSmartBroadcastToggle = viewModel::onSmartBroadcastToggle,
            onPositionFlagsChange = viewModel::onPositionFlagsChange,
            onChannelPositionPrecisionChange = viewModel::onChannelPositionPrecisionChange,
            onWakeLockToggle = viewModel::onWakeLockToggle,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
