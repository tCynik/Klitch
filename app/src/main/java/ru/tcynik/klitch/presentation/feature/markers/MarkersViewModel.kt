package ru.tcynik.klitch.presentation.feature.markers

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MarkersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MarkersUiState())
    val uiState: StateFlow<MarkersUiState> = _uiState.asStateFlow()
}
