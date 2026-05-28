package ru.tcynik.meshtactics.domain.mesh.model

sealed interface MeshConnectionStatus {
    data object Disconnected : MeshConnectionStatus
    data object Scanning : MeshConnectionStatus
    data class Connecting(val deviceName: String) : MeshConnectionStatus
    data class Connected(
        val nodeId: String,
        val shortName: String,
        val deviceName: String,
        val rssi: Int,
        val batteryLevel: Int,
    ) : MeshConnectionStatus
    data object DeviceSleep : MeshConnectionStatus
    data class Error(val message: String) : MeshConnectionStatus
}
