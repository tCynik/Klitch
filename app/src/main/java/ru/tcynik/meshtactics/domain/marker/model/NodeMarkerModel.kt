package ru.tcynik.meshtactics.domain.marker.model

data class NodeMarkerModel(
    val nodeId: String,
    val longName: String,
    val position: GeoPoint,
    val isOnline: Boolean,
    /** Bearing in degrees (0–359), null when node is stationary or heading unknown. */
    val heading: Float?,
)
