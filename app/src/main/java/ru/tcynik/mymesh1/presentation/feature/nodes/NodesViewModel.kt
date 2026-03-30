package ru.tcynik.mymesh1.presentation.feature.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.tcynik.mymesh1.domain.usecase.base.NoParams
import ru.tcynik.mymesh1.domain.usecase.node.GetNodesUseCase

class NodesViewModel(
    getNodes: GetNodesUseCase,
) : ViewModel() {

    val uiState: StateFlow<NodesUiState> = getNodes(NoParams)
        .map { nodes ->
            NodesUiState(nodes = nodes.toImmutableList())
        }
        .catch { e ->
            emit(NodesUiState(error = e.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NodesUiState(isLoading = true),
        )
}
