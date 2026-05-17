package ru.tcynik.meshtactics.presentation.feature.settings

import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.presentation.feature.settings.models.MapItem

data class SettingsUiState(
    val markerSizeLevel: Int = 5,
    val markerSizeLevelPending: Int = 5,
    val mapItems: List<MapItem> = emptyList(),
    val deleteConfirmId: String? = null,
    val tileCacheMode: TileCacheMode = TileCacheMode.DEFAULT,
)
