package ru.tcynik.meshtactics.presentation.feature.nodes

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.model.NodeModel

data class NodesUiState(
    val nodes: ImmutableList<NodeModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
