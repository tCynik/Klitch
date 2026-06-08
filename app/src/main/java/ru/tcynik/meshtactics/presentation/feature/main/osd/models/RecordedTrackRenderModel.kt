package ru.tcynik.meshtactics.presentation.feature.main.osd.models

data class RecordedTrackRenderModel(
    val id: String,
    val color: Int,
    val isRecording: Boolean,
    val points: List<Pair<Double, Double>>,
)
