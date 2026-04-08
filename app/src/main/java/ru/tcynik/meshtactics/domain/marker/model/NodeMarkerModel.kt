package ru.tcynik.meshtactics.domain.marker.model

data class NodeMarkerModel(
    val nodeId: String,
    val longName: String,
    val position: GeoPoint,
    val isOnline: Boolean,
)
