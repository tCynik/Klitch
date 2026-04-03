package ru.tcynik.meshtactics.presentation.feature.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.domain.map.repository.MapTileRepository

class MainViewModel(
    mapTileRepository: MapTileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(tileUrlTemplate = mapTileRepository.getTileUrlTemplate()) }
    }
}
