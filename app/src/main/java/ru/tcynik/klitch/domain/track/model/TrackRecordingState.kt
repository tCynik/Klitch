package ru.tcynik.klitch.domain.track.model

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
        /** Total active recording seconds accumulated across all pause/resume cycles before current active period. */
        val accumulatedSeconds: Long = 0L,
        /** Unix seconds when the current active period started (reset on each resume). */
        val activeFromSeconds: Long = startedAtSeconds,
    ) : TrackRecordingState
}
