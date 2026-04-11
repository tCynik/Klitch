package ru.tcynik.meshtactics.domain.mesh.model

data class GeoNodeModel(
    val nodeId: String,
    val shortName: String,
    /** Straight-line distance from our node in metres. Null when our position is unknown. */
    val distanceMeters: Int?,
    /** Unix timestamp (seconds) of the last GPS position report. */
    val positionTime: Int,
)
