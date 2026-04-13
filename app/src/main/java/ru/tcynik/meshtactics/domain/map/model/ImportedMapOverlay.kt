package ru.tcynik.meshtactics.domain.map.model

data class ImportedMapOverlay(
    val id: String,
    val name: String,
    val uri: String,
    val createdAt: Long,
    val isSelected: Boolean,
)
