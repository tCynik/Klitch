package ru.tcynik.meshtactics.presentation.feature.network.state

sealed interface MeshConnectionStatusUi {
    data object Disconnected : MeshConnectionStatusUi
    data object Scanning : MeshConnectionStatusUi
    data object Syncing : MeshConnectionStatusUi
    data object Rebooting : MeshConnectionStatusUi
    data object WaitingForNode : MeshConnectionStatusUi
    data class Connecting(val deviceName: String) : MeshConnectionStatusUi
    data class Connected(
        val nodeId: String,
        val deviceName: String,
        val rssi: Int,
        val batteryLevel: Int?,
    ) : MeshConnectionStatusUi
    data class Error(val message: String) : MeshConnectionStatusUi
}
