package ru.tcynik.klitch.presentation.feature.marks.models

import ru.tcynik.klitch.presentation.ui.UiText

data class RecordedTrackListItemUiModel(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val startedAtLabel: String,
    val durationLabel: UiText,
    val distanceLabel: UiText,
    val isVisible: Boolean,
)
