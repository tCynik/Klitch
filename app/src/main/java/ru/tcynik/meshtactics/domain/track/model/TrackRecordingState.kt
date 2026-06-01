package ru.tcynik.meshtactics.domain.track.model

sealed interface TrackRecordingState {
    data object Idle : TrackRecordingState

    data class Recording(
        val trackId: String,
        val name: String,
        /** Unix seconds. */
        val startedAtSeconds: Long,
        val settings: TrackRecordingSettings,
        val distanceMeters: Double = 0.0,
        val pointCount: Int = 0,
        val isPaused: Boolean = false,
    ) : TrackRecordingState
}
