package ru.tcynik.meshtactics.presentation.feature.main.osd.models

data class MenuDrawerUiState(
    val isOpen: Boolean,
    val radio: HudButtonSlot,
    val settings: HudButtonSlot,
    val onDismiss: () -> Unit,
)
