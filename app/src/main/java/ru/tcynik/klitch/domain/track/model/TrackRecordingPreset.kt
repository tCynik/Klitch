package ru.tcynik.klitch.domain.track.model

enum class TrackRecordingPreset {
    WALKING, BICYCLE, MOTO, CAR, AIRPLANE, CUSTOM;

    /** null = "-" (distance-only mode). */
    fun defaultIntervalSeconds(): Int? = when (this) {
        WALKING  -> 10
        BICYCLE  -> 5
        MOTO     -> 5
        CAR      -> 5
        AIRPLANE -> 10
        CUSTOM   -> 10
    }

    fun defaultMinDistanceMeters(): Int = when (this) {
        WALKING  -> 5
        BICYCLE  -> 15
        MOTO     -> 30
        CAR      -> 50
        AIRPLANE -> 200
        CUSTOM   -> 5
    }
}
