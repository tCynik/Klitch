package ru.tcynik.meshtactics.domain.track.model

data class RecordedTrack(
    val id: String,
    val name: String,
    /** Unix seconds. */
    val startedAt: Long,
    /** Unix seconds. null = recording not yet finished. */
    val finishedAt: Long?,
    val totalDistanceMeters: Double,
    val color: Int,
    val isVisible: Boolean,
)
