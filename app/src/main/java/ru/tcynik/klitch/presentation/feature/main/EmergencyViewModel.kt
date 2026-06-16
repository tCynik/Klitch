package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.chat.usecase.SyncEmergencyMuteUseCase
import ru.tcynik.klitch.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.emergency.usecase.TriggerEmergencyUseCase

data class EmergencyUiState(
    val isSosActive: Boolean = false,
    val showSosRestoredDialog: Boolean = false,
    val showSosTriggerDialog: Boolean = false,
    val showSosCancelDialog: Boolean = false,
)

class EmergencyViewModel(
    private val observeEmergencyMode: ObserveEmergencyModeUseCase,
    private val triggerEmergency: TriggerEmergencyUseCase,
    private val cancelEmergency: CancelEmergencyUseCase,
    syncEmergencyMute: SyncEmergencyMuteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    init {
        syncEmergencyMute.observe().launchIn(viewModelScope)

        observeEmergencyMode()
            .onEach { active -> _uiState.update { it.copy(isSosActive = active) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            if (observeEmergencyMode().first()) {
                _uiState.update { it.copy(showSosRestoredDialog = true) }
            }
        }
    }

    fun onSosButtonClick() {
        if (_uiState.value.isSosActive) {
            _uiState.update { it.copy(showSosCancelDialog = true) }
        } else {
            _uiState.update { it.copy(showSosTriggerDialog = true) }
        }
    }

    fun onSosRestoredKeep() {
        _uiState.update { it.copy(showSosRestoredDialog = false) }
    }

    fun onSosRestoredDisable() {
        _uiState.update { it.copy(showSosRestoredDialog = false) }
        viewModelScope.launch { cancelEmergency() }
    }

    fun onTriggerSosConfirm() {
        _uiState.update { it.copy(showSosTriggerDialog = false) }
        viewModelScope.launch { triggerEmergency() }
    }

    fun onCancelSosConfirm() {
        _uiState.update { it.copy(showSosCancelDialog = false) }
        viewModelScope.launch { cancelEmergency() }
    }

    fun onDismissSosDialog() {
        _uiState.update { it.copy(showSosTriggerDialog = false, showSosCancelDialog = false) }
    }
}
