package ru.tcynik.klitch.domain.gps.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode

/** Source of the device's current position, abstracting where the fix comes from (phone GPS chip or node telemetry). */
interface PositionSource {
    val mode: PositionSourceMode
    fun observePosition(): Flow<GpsLocation>
}
