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
    /** `position.gps_update_interval` — how often the node's own GPS chip attempts a fix, in seconds. 0 = firmware default (30s). */
    val gpsUpdateIntervalSecs: Int = 0,
)

enum class GpsMode { DISABLED, ENABLED, NOT_PRESENT }

/**
 * Single source of truth for the PHONE_GPS-scenario (`GpsMode.DISABLED`) position-broadcast
 * intent — the app drives position via `sendPosition`, so the node's own autonomous broadcast
 * must stay fully disabled. Firmware stamps `current_time` on every autonomous re-broadcast,
 * which would falsely refresh a peer's view of this node's position even after the phone
 * disconnects (see `docs/features/gps-position-staleness.md`).
 *
 * Read by `NodeProvisioningUseCase` (writes it), `MeshConfigRepositoryImpl` and
 * `SyncContoursOnConnectUseCase` (write the same intent via `prepareNodeForAppDrivenBroadcast`/
 * `disableNodePositionBroadcast`), and `LocationConfigUi.sharingStatus` (validates it) — one
 * constant so these can't drift apart again. See `docs/plans/position-broadcast-interval-unification.md`.
 */
object LocationConfigDefaults {
    const val APP_DRIVEN_BROADCAST_SECS = Int.MAX_VALUE
}
