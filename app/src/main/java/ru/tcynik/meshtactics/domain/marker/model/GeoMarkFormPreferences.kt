package ru.tcynik.meshtactics.domain.marker.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoMarkFormPreferences(
    val selectedType: String = GeoMarkType.POINT.name,
    val selectedColor: Int = 0,
    val selectedTrackEndType: Int = TrackEndType.NONE.ends.toInt(),
    val selectedTtlSeconds: Long = 28800L,
    val markName: String = "",
    val nameCounter: Int = 1,
    val selectedContourId: String = "",
)
