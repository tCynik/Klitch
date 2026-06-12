package ru.tcynik.klitch.domain.map.model

data class ImportedMapOverlay(
    val id: String,
    val name: String,
    val uri: String,
    val createdAt: Long,
    val isSelected: Boolean,
    val geoJsonPath: String?,
    val groundOverlayPath: String?,
)
