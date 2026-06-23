package ru.tcynik.klitch.presentation.feature.network.state.models

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigDefaults

data class LocationConfigUi(
    val provideLocationToMesh: Boolean,
    val hasLocationPermission: Boolean,
    val gpsMode: GpsModeUi,
    val fixedPositionEnabled: Boolean,
    val broadcastIntervalSecs: Int,
    val smartBroadcastEnabled: Boolean,
    val smartBroadcastMinDistanceM: Int,
    val positionFlags: Int,
    val primaryChannelPositionPrecision: Int,
) {
    val sharingStatus: LocationSharingStatus get() = computeSharingStatus()

    private fun computeSharingStatus(): LocationSharingStatus {
        val blockers = mutableListOf<BlockReason>()
        val warnings = mutableListOf<BlockReason>()

        if (!provideLocationToMesh)               blockers += BlockReason.PROVIDE_LOCATION_DISABLED
        if (!hasLocationPermission)               blockers += BlockReason.LOCATION_PERMISSION_DENIED
        if (fixedPositionEnabled)                 blockers += BlockReason.FIXED_POSITION_ACTIVE
        if (primaryChannelPositionPrecision == 0) blockers += BlockReason.CHANNEL_PRECISION_DISABLED

        if (positionFlags == 0)                   warnings += BlockReason.NO_POSITION_FLAGS
        // Warn only on actual misconfiguration, not on any value above an arbitrary threshold —
        // the Klitch preset deliberately uses a high anchor interval (keepalive via DeviceMetrics,
        // not the live-position channel — that's sendPosition). Anything else means auto-config
        // hasn't run yet or something external changed it.
        if (broadcastIntervalSecs != LocationConfigDefaults.BROADCAST_INTERVAL_SECS)
            warnings += BlockReason.BROADCAST_INTERVAL_HIGH
        if (gpsMode == GpsModeUi.ENABLED)         warnings += BlockReason.GPS_MODE_CONFLICT

        return when {
            blockers.isNotEmpty() -> LocationSharingStatus.Blocked(blockers)
            warnings.isNotEmpty() -> LocationSharingStatus.Warning(warnings)
            else -> LocationSharingStatus.Ready
        }
    }
}

enum class GpsModeUi { DISABLED, ENABLED, NOT_PRESENT }

fun GpsMode.toUi(): GpsModeUi = when (this) {
    GpsMode.DISABLED -> GpsModeUi.DISABLED
    GpsMode.ENABLED -> GpsModeUi.ENABLED
    GpsMode.NOT_PRESENT -> GpsModeUi.NOT_PRESENT
}

fun GpsModeUi.toDomain(): GpsMode = when (this) {
    GpsModeUi.DISABLED -> GpsMode.DISABLED
    GpsModeUi.ENABLED -> GpsMode.ENABLED
    GpsModeUi.NOT_PRESENT -> GpsMode.NOT_PRESENT
}

object LocationConfigOptions {
    val broadcastIntervalSecs = listOf(15, 30, 60, 120, 300, 600, 900, 1800)
    val channelPrecisionBits = listOf(0, 10, 13, 16, 19, 32)
    val positionFlagBits = listOf(1, 2, 32, 64, 128, 256, 512)
}

enum class BlockReason {
    PROVIDE_LOCATION_DISABLED,
    LOCATION_PERMISSION_DENIED,
    FIXED_POSITION_ACTIVE,
    CHANNEL_PRECISION_DISABLED,
    NO_POSITION_FLAGS,
    BROADCAST_INTERVAL_HIGH,
    GPS_MODE_CONFLICT,
}

sealed class LocationSharingStatus {
    object Ready : LocationSharingStatus()
    data class Warning(val reasons: List<BlockReason>) : LocationSharingStatus()
    data class Blocked(val reasons: List<BlockReason>) : LocationSharingStatus()
}
