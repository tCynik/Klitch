package ru.tcynik.meshtactics.presentation.feature.node

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NodeSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NodeSettingsUiState())
    val uiState: StateFlow<NodeSettingsUiState> = _uiState.asStateFlow()
}
