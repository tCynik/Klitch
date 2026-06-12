package ru.tcynik.klitch.presentation.feature.nodes

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.domain.mesh.model.GeoNodeModel

data class NodesUiState(
    val nodes: ImmutableList<GeoNodeModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
