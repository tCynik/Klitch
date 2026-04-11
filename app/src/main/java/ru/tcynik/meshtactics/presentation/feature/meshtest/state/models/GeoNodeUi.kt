package ru.tcynik.meshtactics.presentation.feature.meshtest.state.models

data class GeoNodeUi(
    val nodeId: String,
    val shortName: String,
    /** Pre-formatted distance string, e.g. "1.2 km", "340 m", "—". */
    val distanceFormatted: String,
    /** Unix timestamp (seconds) of the last GPS report. Used by the composable ticker. */
    val positionTime: Int,
    /** Ground speed in m/s (0 if unknown or stationary). */
    val groundSpeed: Int = 0,
    /** Ground track (bearing) in degrees 0–359, clockwise from north. 0 if unknown. */
    val groundTrack: Int = 0,
)
