package ru.tcynik.klitch.domain.location.model

/**
 * GPS signal status enriched with a classified signal level.
 */
data class GpsStatusModel(
    val satelliteCount: Int,
    val accuracyMeters: Float?,
    val signalLevel: GpsSignalLevel,
) {
    companion object {
        val None = GpsStatusModel(
            satelliteCount = 0,
            accuracyMeters = null,
            signalLevel = GpsSignalLevel.None,
        )
    }
}
