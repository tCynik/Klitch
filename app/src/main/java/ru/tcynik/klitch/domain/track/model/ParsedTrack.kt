package ru.tcynik.klitch.domain.track.model

data class ParsedTrack(
    val name: String,
    val points: List<Pair<Double, Double>>,
)
