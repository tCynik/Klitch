package ru.tcynik.mymesh1.presentation.feature.meshtest

import ru.tcynik.mymesh1.presentation.feature.meshtest.state.ConfigTabState
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.ConnectionTabState
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.LogTabState
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshConnectionStatusUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshTestTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MessagesTabState
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.TelemetryTabState

data class MeshTestUiState(
    val selectedTab: MeshTestTab = MeshTestTab.Connection,
    val connectionStatus: MeshConnectionStatusUi = MeshConnectionStatusUi.Disconnected,
    val connectionTab: ConnectionTabState = ConnectionTabState(),
    val messagesTab: MessagesTabState = MessagesTabState(),
    val configTab: ConfigTabState = ConfigTabState(),
    val telemetryTab: TelemetryTabState = TelemetryTabState(),
    val logTab: LogTabState = LogTabState(),
)
