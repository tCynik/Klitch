package ru.tcynik.meshtactics.presentation.feature.nodes.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.presentation.feature.nodes.state.models.GeoNodeUi

data class GeoNodesListState(
    val nodes: ImmutableList<GeoNodeUi> = persistentListOf(),
)
