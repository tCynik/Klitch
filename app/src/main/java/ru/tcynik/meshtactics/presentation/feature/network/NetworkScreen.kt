package ru.tcynik.meshtactics.presentation.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.presentation.feature.network.components.CallsignGateDialog
import ru.tcynik.meshtactics.presentation.feature.network.components.ConnectionContent
import ru.tcynik.meshtactics.presentation.feature.network.components.DisconnectConfirmDialog
import ru.tcynik.meshtactics.presentation.feature.network.components.MeshStatusBar
import ru.tcynik.meshtactics.presentation.feature.network.components.TelemetryContent
import ru.tcynik.meshtactics.presentation.ui.components.SyncRequiredDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: NetworkViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    state.callsignGateDialog?.let { dialog ->
        CallsignGateDialog(
            callsignInput = dialog.callsignInput,
            onCallsignChange = viewModel::onCallsignInput,
            onConfirm = viewModel::onCallsignConfirmed,
            onDismiss = viewModel::onCallsignDismissed,
        )
    }

    if (state.showSyncDialog) {
        SyncRequiredDialog(
            onConfirm = viewModel::onConfirmChannelSync,
            onDismiss = viewModel::onDismissChannelSync,
        )
    }

    if (state.showDisconnectDialog) {
        DisconnectConfirmDialog(
            deviceName = state.lastConnectedNodeName,
            onConfirm = viewModel::onDisconnectConfirmed,
            onDismiss = viewModel::onDisconnectDismissed,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сеть") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            text = "Использовать",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(
                            checked = state.networkEnabled,
                            onCheckedChange = viewModel::onNetworkEnabledToggle,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.networkEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    MeshStatusBar(
                        status = state.connectionStatus,
                        rebootingNodeName = state.lastConnectedNodeName,
                        onDisconnectClick = viewModel::onDisconnectClick,
                        onSettingsClick = onNavigateToSettings,
                    )
                    HorizontalDivider()
                    ConnectionContent(
                        state = state.connection,
                        connectionStatus = state.connectionStatus,
                        onScanClick = viewModel::onScanClick,
                        onStopScanClick = viewModel::onStopScanClick,
                        onConnectClick = viewModel::onConnectClick,
                    )
                    HorizontalDivider()
                    TelemetryContent(
                        state = state.telemetry,
                        connectionStatus = state.connectionStatus,
                        onRefreshClick = viewModel::onRefreshTelemetryClick,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Для подключения к сети\nMeshtastic включите сеть",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
