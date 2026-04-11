package ru.tcynik.meshtactics.presentation.feature.settings

data class SettingsUiState(
    val selectedTab: SettingsTab = SettingsTab.Map,
    val markerSizeLevel: Int = 5,
    val markerSizeLevelPending: Int = 5,
)
