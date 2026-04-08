package ru.tcynik.meshtactics.domain.marker.model

data class MarkerModel(
    val id: String,
    val title: String,
    val description: String,
    val position: GeoPoint,
    val createdAt: Long,
    val expiresAt: Long?,
    val isSharedToChannel: Boolean,
    val authorNodeId: String,
)
