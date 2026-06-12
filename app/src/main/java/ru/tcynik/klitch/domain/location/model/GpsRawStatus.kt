package ru.tcynik.klitch.domain.location.model

/**
 * Raw GPS data from the OS — no business classification applied.
 *
 * @param satelliteCount number of satellites currently used in the fix
 * @param accuracyMeters horizontal accuracy in metres, or null if no fix
 */
data class GpsRawStatus(
    val satelliteCount: Int,
    val accuracyMeters: Float?,
)
