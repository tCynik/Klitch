package ru.tcynik.meshtactics.presentation.feature.meshtest.state.models

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
        if (broadcastIntervalSecs > 120)          warnings += BlockReason.BROADCAST_INTERVAL_HIGH
        if (gpsMode == GpsModeUi.ENABLED)         warnings += BlockReason.GPS_MODE_CONFLICT

        return when {
            blockers.isNotEmpty() -> LocationSharingStatus.Blocked(blockers)
            warnings.isNotEmpty() -> LocationSharingStatus.Warning(warnings)
            else -> LocationSharingStatus.Ready
        }
    }
}

enum class GpsModeUi { DISABLED, ENABLED, NOT_PRESENT }

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
