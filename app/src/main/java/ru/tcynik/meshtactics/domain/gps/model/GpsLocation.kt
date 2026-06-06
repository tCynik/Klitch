package ru.tcynik.meshtactics.domain.gps.model

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,
    val speed: Float?,
    val accuracy: Float,
    val elapsedRealtimeNanos: Long,
    /** Unix epoch milliseconds of the GPS fix (from [android.location.Location.getTime]). */
    val time: Long,
)
