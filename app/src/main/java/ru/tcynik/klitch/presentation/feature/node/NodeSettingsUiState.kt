package ru.tcynik.klitch.presentation.feature.node

data class NodeSettingsUiState(
    val isLoading: Boolean = false,
    val publicKeyHex: String? = null,
    val hasKey: Boolean = false,
    val isMismatch: Boolean = false,
    val showRegenerateDialog: Boolean = false,
    val isNodeConnected: Boolean = false,
)
