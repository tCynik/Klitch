package ru.tcynik.klitch.presentation.feature.network.state.models

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigDefaults
import ru.tcynik.klitch.mesh.service.PositionTrackingPolicy

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
        // Expected broadcastIntervalSecs differs by mode — DISABLED means the app drives position
        // (node must stay fully silent), ENABLED means the node tracks+broadcasts itself (cadence
        // derived from the same constant BackgroundPositionSession.ensureNodeGpsPreset() writes).
        // NOT_PRESENT has no GPS chip — broadcastIntervalSecs isn't relevant, skip the check.
        val expectedBroadcastSecs = when (gpsMode) {
            GpsModeUi.DISABLED -> LocationConfigDefaults.APP_DRIVEN_BROADCAST_SECS
            GpsModeUi.ENABLED -> PositionTrackingPolicy.STATIONARY_INTERVAL_SECS
            GpsModeUi.NOT_PRESENT -> null
        }
        if (expectedBroadcastSecs != null && broadcastIntervalSecs != expectedBroadcastSecs)
            warnings += BlockReason.BROADCAST_INTERVAL_HIGH

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
}

sealed class LocationSharingStatus {
    object Ready : LocationSharingStatus()
    data class Warning(val reasons: List<BlockReason>) : LocationSharingStatus()
    data class Blocked(val reasons: List<BlockReason>) : LocationSharingStatus()
}
