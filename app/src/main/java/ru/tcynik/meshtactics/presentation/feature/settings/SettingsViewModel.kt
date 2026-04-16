package ru.tcynik.meshtactics.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.presentation.feature.settings.models.MapItem

class SettingsViewModel(
    private val repository: MarkerSettingsRepository,
    private val observeImportedMaps: ObserveImportedMapsUseCase,
    private val importMapFile: ImportMapFileUseCase,
    private val hideImportedMap: HideImportedMapUseCase,
    private val deleteImportedMap: DeleteImportedMapUseCase,
    private val toggleImportedMapSelection: ToggleImportedMapSelectionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            markerSizeLevel = repository.getMarkerSizeLevel(),
            markerSizeLevelPending = repository.getMarkerSizeLevel(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeImportedMaps()
            .onEach { overlays ->
                _uiState.update { state ->
                    state.copy(
                        mapItems = overlays.map { overlay ->
                            MapItem(
                                id = overlay.id,
                                name = overlay.name,
                                createdAt = overlay.createdAt,
                                isSelected = overlay.isSelected,
                            )
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onTabSelected(tab: SettingsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onMarkerSizeLevelChange(level: Int) {
        _uiState.update { it.copy(markerSizeLevelPending = level) }
    }

    fun onSave() {
        val pending = _uiState.value.markerSizeLevelPending
        repository.setMarkerSizeLevel(pending)
        _uiState.update { it.copy(markerSizeLevel = pending) }
    }

    fun onAddMap(uri: String, name: String) {
        viewModelScope.launch {
            importMapFile(uri = uri, name = name, createdAt = System.currentTimeMillis())
        }
    }

    fun onHideMap(id: String) {
        viewModelScope.launch {
            hideImportedMap(id)
        }
    }

    fun onRequestDeleteMap(id: String) {
        _uiState.update { it.copy(deleteConfirmId = id) }
    }

    fun onConfirmDelete() {
        val id = _uiState.value.deleteConfirmId ?: return
        _uiState.update { it.copy(deleteConfirmId = null) }
        viewModelScope.launch {
            deleteImportedMap(id)
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(deleteConfirmId = null) }
    }

    fun onToggleSelection(id: String, selected: Boolean) {
        viewModelScope.launch {
            toggleImportedMapSelection(id, selected)
        }
    }
}
