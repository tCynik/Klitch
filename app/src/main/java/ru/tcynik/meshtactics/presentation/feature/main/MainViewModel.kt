package ru.tcynik.meshtactics.presentation.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
    observeNodeMarkers: ObserveNodeMarkersUseCase,
    observeConnectionStatus: ObserveConnectionStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { state ->
            state.copy(
                tileUrlTemplate = getTileUrl(),
                initialCameraPosition = getLastPosition() ?: state.initialCameraPosition,
            )
        }

        observeNodeMarkers(NoParams)
            .onEach { markers ->
                _uiState.update { it.copy(nodeMarkers = markers.toImmutableList()) }
            }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams)
            .onEach { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
            .launchIn(viewModelScope)
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
    }
}
