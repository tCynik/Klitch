package ru.tcynik.meshtactics.presentation.feature.network

import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkSettingsState

data class NetworkSettingsUiState(
    val connectionStatus: MeshConnectionStatusUi = MeshConnectionStatusUi.Disconnected,
    val settings: NetworkSettingsState = NetworkSettingsState(),
)
