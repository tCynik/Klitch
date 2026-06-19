package ru.tcynik.klitch.presentation.feature.network

import ru.tcynik.klitch.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.klitch.presentation.feature.network.state.NetworkSettingsState

data class NetworkSettingsUiState(
    val connectionStatus: MeshConnectionStatusUi = MeshConnectionStatusUi.Disconnected,
    val settings: NetworkSettingsState = NetworkSettingsState(),
    val syncRequired: Boolean = false,
    val showLeaveSyncDialog: Boolean = false,
)
