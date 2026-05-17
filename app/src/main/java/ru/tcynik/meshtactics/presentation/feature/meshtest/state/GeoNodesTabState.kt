package ru.tcynik.meshtactics.presentation.feature.meshtest.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.models.GeoNodeUi

data class GeoNodesTabState(
    val nodes: ImmutableList<GeoNodeUi> = persistentListOf(),
)
