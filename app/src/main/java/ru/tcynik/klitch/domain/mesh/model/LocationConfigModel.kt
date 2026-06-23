package ru.tcynik.klitch.domain.mesh.model

data class LocationConfigModel(
    val provideLocationToMesh: Boolean,
    val hasLocationPermission: Boolean,
    val gpsMode: GpsMode,
    val fixedPositionEnabled: Boolean,
    val broadcastIntervalSecs: Int,
    val smartBroadcastEnabled: Boolean,
    val smartBroadcastMinDistanceM: Int,
    val positionFlags: Int,
    val primaryChannelPositionPrecision: Int,
)

enum class GpsMode { DISABLED, ENABLED, NOT_PRESENT }

/**
 * Klitch position preset values, written by `NodeProvisioningUseCase` and read by the
 * presentation-layer misconfiguration check (`LocationConfigUi.sharingStatus`) — single
 * source of truth so the warning logic can't drift from what auto-config actually sets.
 */
object LocationConfigDefaults {
    const val BROADCAST_INTERVAL_SECS = 1800 // 30 min forced anchor (keepalive via DeviceMetrics)
}
