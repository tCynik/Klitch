package ru.tcynik.meshtactics.presentation.feature.main.osd.models

data class MenuDrawerUiState(
    val isOpen: Boolean,
    val items: List<DrawerMenuItem>,
    val onDismiss: () -> Unit,
)
