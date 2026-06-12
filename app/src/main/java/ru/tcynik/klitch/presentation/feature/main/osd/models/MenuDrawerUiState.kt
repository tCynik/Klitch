package ru.tcynik.klitch.presentation.feature.main.osd.models

data class MenuDrawerUiState(
    val isOpen: Boolean,
    val items: List<DrawerMenuItem>,
    val onDismiss: () -> Unit,
    val isSosActive: Boolean = false,
    val onSosClick: () -> Unit = {},
)
