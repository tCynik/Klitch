package ru.tcynik.klitch.presentation.feature.markers

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel

data class MarkersUiState(
    val markers: ImmutableList<GeoMarkModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
