package ru.tcynik.klitch.domain.track.model

data class TrackRecordingSettings(
    val preset: TrackRecordingPreset = TrackRecordingPreset.WALKING,
    /** null = "-" mode (distance-only trigger). */
    val intervalSeconds: Int? = TrackRecordingPreset.WALKING.defaultIntervalSeconds(),
    val minDistanceMeters: Int = TrackRecordingPreset.WALKING.defaultMinDistanceMeters(),
    val name: String = "Трек",
    val nameCounter: Int? = 1,
    val color: Int = 0,
)
