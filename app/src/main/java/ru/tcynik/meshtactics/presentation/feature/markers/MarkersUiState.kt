package ru.tcynik.meshtactics.presentation.feature.markers

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.marker.model.MarkerModel

data class MarkersUiState(
    val markers: ImmutableList<MarkerModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
