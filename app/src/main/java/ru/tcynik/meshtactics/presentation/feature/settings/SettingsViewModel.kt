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
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.settings.models.MapItem

class SettingsViewModel(
    private val repository: MarkerSettingsRepository,
    private val observeImportedMaps: ObserveImportedMapsUseCase,
    private val importMapFile: ImportMapFileUseCase,
    private val hideImportedMap: HideImportedMapUseCase,
    private val deleteImportedMap: DeleteImportedMapUseCase,
    private val toggleImportedMapSelection: ToggleImportedMapSelectionUseCase,
    private val getTileCacheMode: GetTileCacheModeUseCase,
    private val observeTileCacheMode: ObserveTileCacheModeUseCase,
    private val setTileCacheMode: SetTileCacheModeUseCase,
    private val getScreenOrientationLocked: GetScreenOrientationLockedUseCase,
    private val getScreenOrientationMode: GetScreenOrientationModeUseCase,
    private val setScreenOrientationLocked: SetScreenOrientationLockedUseCase,
    private val setScreenOrientationMode: SetScreenOrientationModeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            markerSizeLevel = repository.getMarkerSizeLevel(),
            markerSizeLevelPending = repository.getMarkerSizeLevel(),
            geoMarkSizeLevelPending = repository.getGeoMarkSizeLevel(),
            showGeoMarkNamesPending = repository.getShowGeoMarkNames(),
            orientationLockedPending = getScreenOrientationLocked(),
            orientationModePending = getScreenOrientationMode(),
            tileCacheMode = getTileCacheMode(),
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

        observeTileCacheMode(NoParams)
            .onEach { mode -> _uiState.update { it.copy(tileCacheMode = mode) } }
            .launchIn(viewModelScope)
    }

    fun onMarkerSizeLevelChange(level: Int) {
        _uiState.update { it.copy(markerSizeLevelPending = level) }
    }

    fun onGeoMarkSizeLevelChange(level: Int) {
        _uiState.update { it.copy(geoMarkSizeLevelPending = level) }
    }

    fun onShowGeoMarkNamesChange(enabled: Boolean) {
        _uiState.update { it.copy(showGeoMarkNamesPending = enabled) }
    }

    fun onOrientationLockedChange(locked: Boolean) {
        _uiState.update { it.copy(orientationLockedPending = locked) }
    }

    fun onOrientationModeChange(mode: ScreenOrientationMode) {
        _uiState.update { it.copy(orientationModePending = mode) }
    }

    fun onSave() {
        val state = _uiState.value
        repository.setMarkerSizeLevel(state.markerSizeLevelPending)
        repository.setGeoMarkSizeLevel(state.geoMarkSizeLevelPending)
        repository.setShowGeoMarkNames(state.showGeoMarkNamesPending)
        setScreenOrientationLocked(state.orientationLockedPending)
        setScreenOrientationMode(state.orientationModePending)
        _uiState.update { it.copy(markerSizeLevel = state.markerSizeLevelPending) }
    }

    fun onTileCacheModeSelected(mode: TileCacheMode) {
        setTileCacheMode(mode)
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
