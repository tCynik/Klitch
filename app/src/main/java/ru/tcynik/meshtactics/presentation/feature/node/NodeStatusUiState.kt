package ru.tcynik.meshtactics.presentation.feature.node

data class NodeStatusUiState(
    val isLoading: Boolean = false,
    val connectionStatus: String = "",
)
