package ru.tcynik.klitch.domain.mesh.model

data class GeoNodeModel(
    val nodeId: String,
    val shortName: String,
    /** Straight-line distance from our node in metres. Null when our position is unknown. */
    val distanceMeters: Int?,
    /** Unix timestamp (seconds) of the last GPS position report. */
    val positionTime: Int,
    /** Ground speed in m/s (0 if unknown or stationary). */
    val groundSpeed: Int = 0,
    /** Ground track (bearing) in degrees 0–359, clockwise from north. 0 if unknown. */
    val groundTrack: Int = 0,
)
