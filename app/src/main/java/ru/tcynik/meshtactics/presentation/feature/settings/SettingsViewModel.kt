package ru.tcynik.meshtactics.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.data.settings.AppSettings

class SettingsViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            markerSizeLevel = appSettings.getMarkerSizeLevel(),
            markerSizeLevelPending = appSettings.getMarkerSizeLevel(),
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
        appSettings.setMarkerSizeLevel(pending)
        _uiState.update { it.copy(markerSizeLevel = pending) }
    }
}
