package ru.tcynik.klitch.presentation.feature.marks.models

data class RecordedTrackListItemUiModel(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val startedAtLabel: String,
    val durationLabel: String,
    val distanceLabel: String,
    val isVisible: Boolean,
)
