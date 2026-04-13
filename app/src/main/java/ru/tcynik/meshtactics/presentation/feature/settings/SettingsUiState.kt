package ru.tcynik.meshtactics.presentation.feature.settings

import java.time.LocalDateTime

data class SettingsUiState(
    val selectedTab: SettingsTab = SettingsTab.Map,
    val markerSizeLevel: Int = 5,
    val markerSizeLevelPending: Int = 5,
    val mapItems: List<MapItem> = listOf(
        MapItem("1", "Основная карта", LocalDateTime.of(2026, 4, 10, 14, 30), true),
        MapItem("2", "Лесной массив", LocalDateTime.of(2026, 4, 9, 10, 15)),
        MapItem("3", "Горный маршрут", LocalDateTime.of(2026, 4, 8, 16, 45)),
        MapItem("4", "Городская зона", LocalDateTime.of(2026, 4, 7, 9, 0)),
        MapItem("5", "Резервная карта", LocalDateTime.of(2026, 4, 5, 12, 20)),
    ),
)
