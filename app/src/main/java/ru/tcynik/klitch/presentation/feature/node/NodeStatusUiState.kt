package ru.tcynik.klitch.presentation.feature.node

data class NodeStatusUiState(
    val isLoading: Boolean = false,
    val connectionStatus: String = "",
)
