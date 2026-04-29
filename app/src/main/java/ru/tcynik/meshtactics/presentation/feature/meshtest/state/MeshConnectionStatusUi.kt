package ru.tcynik.meshtactics.presentation.feature.meshtest.state

sealed interface MeshConnectionStatusUi {
    data object Disconnected : MeshConnectionStatusUi
    data object Scanning : MeshConnectionStatusUi
    data object Rebooting : MeshConnectionStatusUi
    data class Connecting(val deviceName: String) : MeshConnectionStatusUi
    data class Connected(
        val nodeId: String,
        val rssi: Int,
        val batteryLevel: Int?,
    ) : MeshConnectionStatusUi
    data class Error(val message: String) : MeshConnectionStatusUi
}
