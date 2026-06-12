package ru.tcynik.klitch.domain.track.model

data class TrackPoint(
    val trackId: String,
    /** Unix milliseconds. */
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
)
