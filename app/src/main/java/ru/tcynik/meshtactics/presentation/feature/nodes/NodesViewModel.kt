package ru.tcynik.meshtactics.presentation.feature.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGeoNodesUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class NodesViewModel(
    observeGeoNodes: ObserveGeoNodesUseCase,
) : ViewModel() {

    val uiState: StateFlow<NodesUiState> = observeGeoNodes(NoParams)
        .map { nodes -> NodesUiState(nodes = nodes.toImmutableList()) }
        .catch { e -> emit(NodesUiState(error = e.message)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NodesUiState(isLoading = true),
        )
}
