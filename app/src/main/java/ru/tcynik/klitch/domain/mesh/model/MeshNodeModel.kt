package ru.tcynik.klitch.domain.mesh.model

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
    val latitude: Double,
    val longitude: Double,
    val hasValidPosition: Boolean,
    /** Unix timestamp (seconds) of the last GPS position report. 0 if never reported. */
    val positionTime: Int,
    val isOnline: Boolean,
    /** Ground speed in m/s (0 if unknown or stationary). */
    val groundSpeed: Int,
    /** Ground track (bearing) in degrees 0–359, clockwise from north. 0 if unknown. */
    val groundTrack: Int,
    /** Meshtastic channel slot from which the last position packet was received. Null until first packet. */
    val receivedOnSlot: Int? = null,
)
