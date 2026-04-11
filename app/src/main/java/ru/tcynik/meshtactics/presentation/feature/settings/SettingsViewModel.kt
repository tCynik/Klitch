package ru.tcynik.meshtactics.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class SettingsViewModel(
    private val repository: MarkerSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            markerSizeLevel = repository.getMarkerSizeLevel(),
            markerSizeLevelPending = repository.getMarkerSizeLevel(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
}
