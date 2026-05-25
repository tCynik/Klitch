package ru.tcynik.meshtactics.domain.marker.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoMarkFormPreferences(
    val selectedType: String = GeoMarkType.POINT.name,
    val selectedColor: Int = 0,
    val selectedTrackEndType: Int = TrackEndType.NONE.ends.toInt(),
    val selectedTtlSeconds: Long = 900L,
    val pointMarkName: String = "точка",
    val trackMarkName: String = "Путь",
    val pointNameCounter: Int? = 1,
    val trackNameCounter: Int? = 1,
    val selectedContourId: String = "",
    val selectedShape: String = GeoMarkShape.CIRCLE.name,
)
