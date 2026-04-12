package ru.tcynik.meshtactics.domain.marker.model

data class NodeMarkerModel(
    val nodeId: String,
    val longName: String,
    val position: GeoPoint,
    val isOnline: Boolean,
    /** Position older than 2 minutes — shown as grey marker. */
    val isStale: Boolean,
    /** Bearing in degrees (0–359), null when node is stationary or heading unknown. */
    val heading: Float?,
)
