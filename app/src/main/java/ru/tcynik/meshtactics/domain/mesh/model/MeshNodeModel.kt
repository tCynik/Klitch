package ru.tcynik.meshtactics.domain.mesh.model

data class MeshNodeModel(
    val num: Int,
    val nodeId: String,
    val shortName: String,
    val longName: String,
    val snr: Float,
    val rssi: Int,
    val lastHeard: Int,
    val hopsAway: Int,
    val batteryLevel: Int,
    val voltage: Float,
    val channelUtilization: Float,
    val airUtilTx: Float,
    val uptimeSeconds: Long,
)
