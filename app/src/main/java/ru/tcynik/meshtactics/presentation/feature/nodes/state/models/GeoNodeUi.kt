package ru.tcynik.meshtactics.presentation.feature.nodes.state.models

data class GeoNodeUi(
    val nodeId: String,
    val shortName: String,
    val distanceFormatted: String,
    val positionTime: Long,
    val groundSpeed: Float,
    val groundTrack: Int,
)
