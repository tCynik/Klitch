package ru.tcynik.klitch.presentation.feature.marks.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.presentation.ui.UiText

data class GeoMarksDeleteConfirmUi(
    val message: UiText,
    val markIds: ImmutableList<String>,
)
