package ru.tcynik.mymesh1.presentation.feature.meshtest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.MeshStatusBar
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.tabs.ConfigTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.tabs.ConnectionTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.tabs.LogTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.tabs.MessagesTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.components.tabs.TelemetryTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshTestTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTestScreen(
    nodeId: String,
    onNavigateBack: () -> Unit,
    viewModel: MeshTestViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Test") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            MeshStatusBar(
                status = state.connectionStatus,
                onDisconnectClick = viewModel::onDisconnectClick,
            )

            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                MeshTestTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (state.selectedTab) {
                MeshTestTab.Connection -> ConnectionTab(
                    state = state.connectionTab,
                    connectionStatus = state.connectionStatus,
                    onScanClick = viewModel::onScanClick,
                    onStopScanClick = viewModel::onStopScanClick,
                    onConnectClick = viewModel::onConnectClick,
                    modifier = Modifier.fillMaxSize(),
                )
                MeshTestTab.Messages -> MessagesTab(
                    state = state.messagesTab,
                    connectionStatus = state.connectionStatus,
                    onInputChange = viewModel::onInputChange,
                    onSendClick = viewModel::onSendClick,
                    modifier = Modifier.fillMaxSize(),
                )
                MeshTestTab.Config -> ConfigTab(
                    state = state.configTab,
                    connectionStatus = state.connectionStatus,
                    onReadConfigClick = viewModel::onReadConfigClick,
                    onEditConfigClick = viewModel::onEditConfigClick,
                    onWriteConfigClick = viewModel::onWriteConfigClick,
                    modifier = Modifier.fillMaxSize(),
                )
                MeshTestTab.Telemetry -> TelemetryTab(
                    state = state.telemetryTab,
                    connectionStatus = state.connectionStatus,
                    onRefreshClick = viewModel::onRefreshTelemetryClick,
                    modifier = Modifier.fillMaxSize(),
                )
                MeshTestTab.Log -> LogTab(
                    state = state.logTab,
                    onFilterChange = viewModel::onLogFilterChange,
                    onPauseToggle = viewModel::onLogPauseToggle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
