package ru.tcynik.klitch.presentation.feature.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class NodeSettingsViewModel(
    private val observeNodeSecurityConfig: ObserveNodeSecurityConfigUseCase,
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val regeneratePkcKeys: RegeneratePkcKeysUseCase,
    private val rebootNode: RebootNodeUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NodeSettingsUiState())
    val uiState: StateFlow<NodeSettingsUiState> = _uiState.asStateFlow()

    init {
        observeNodeSecurityConfig(NoParams)
            .onEach { model ->
                _uiState.update {
                    it.copy(
                        publicKeyHex = model?.publicKeyHex,
                        hasKey = model?.hasKey ?: false,
                        isMismatch = model?.isMismatch ?: false,
                    )
                }
            }.launchIn(viewModelScope)

        observeConnectionStatus(NoParams)
            .onEach { status ->
                _uiState.update { it.copy(isNodeConnected = status is MeshConnectionStatus.Connected) }
            }.launchIn(viewModelScope)
    }

    fun onRegenerateClick() {
        _uiState.update { it.copy(showRegenerateDialog = true) }
    }

    fun onRegenerateConfirm() {
        _uiState.update { it.copy(showRegenerateDialog = false) }
        logger.i("Node", "NodeSettingsViewModel.onRegenerateConfirm: PKC keys regeneration confirmed — firmware reboot expected")
        regeneratePkcKeys()
        viewModelScope.launch {
            delay(300)
            rebootNode()
        }
    }

    fun onRegenerateDismiss() {
        _uiState.update { it.copy(showRegenerateDialog = false) }
    }
}
