package ru.tcynik.meshtactics.presentation.feature.marks.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class GeoMarksListUiState(
    val items: ImmutableList<GeoMarkListItemUiModel> = persistentListOf(),
    val hasMarks: Boolean = false,
    val deliveryFilters: ImmutableList<GeoMarkDeliveryFilterButtonUi> = persistentListOf(),
    /** Все метки текущего фильтра отмечены чекбоксом (видны на карте). */
    val allFilteredVisible: Boolean = false,
    /** Есть метки в текущем фильтре — кнопка «все/снять» активна. */
    val bulkVisibilityEnabled: Boolean = false,
)
