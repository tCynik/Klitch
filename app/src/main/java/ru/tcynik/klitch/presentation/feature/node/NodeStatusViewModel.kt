package ru.tcynik.klitch.presentation.feature.node

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NodeStatusViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NodeStatusUiState())
    val uiState: StateFlow<NodeStatusUiState> = _uiState.asStateFlow()
}
