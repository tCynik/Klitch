package ru.tcynik.meshtactics.presentation.feature.network

import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkConnectionState
import ru.tcynik.meshtactics.presentation.feature.network.state.NetworkTelemetryState
import ru.tcynik.meshtactics.presentation.feature.network.state.models.CallsignGateDialogState

data class NetworkUiState(
    val networkEnabled: Boolean = true,
    val connectionStatus: MeshConnectionStatusUi = MeshConnectionStatusUi.Disconnected,
    val connection: NetworkConnectionState = NetworkConnectionState(),
    val telemetry: NetworkTelemetryState = NetworkTelemetryState(),
    val showSyncDialog: Boolean = false,
    val showDisconnectDialog: Boolean = false,
    val callsignGateDialog: CallsignGateDialogState? = null,
    val isRebooting: Boolean = false,
    val lastConnectedNodeName: String = "",
)
