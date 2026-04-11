package ru.tcynik.meshtactics.domain.mesh.model

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
