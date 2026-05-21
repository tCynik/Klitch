package ru.tcynik.meshtactics.presentation.feature.marks.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class GeoMarksListUiState(
    val items: ImmutableList<GeoMarkListItemUiModel> = persistentListOf(),
)
