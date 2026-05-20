package ru.tcynik.meshtactics.domain.marker.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoMarkPreset(
    val id: String,
    val displayName: String,
    val prefs: GeoMarkFormPreferences,
)
