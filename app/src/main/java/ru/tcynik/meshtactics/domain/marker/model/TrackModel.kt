package ru.tcynik.meshtactics.domain.marker.model

data class TrackModel(
    val id: String,
    val title: String,
    val points: List<GeoPoint>,
    val recordedAt: Long,
    val authorNodeId: String,
)
