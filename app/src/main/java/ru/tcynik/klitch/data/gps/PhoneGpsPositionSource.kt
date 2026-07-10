package ru.tcynik.klitch.data.gps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.gps.repository.PositionSource

class PhoneGpsPositionSource(private val gpsRepository: GpsRepository) : PositionSource {
    override val mode: PositionSourceMode = PositionSourceMode.PHONE_GPS

    override fun observePosition(): Flow<GpsLocation> = gpsRepository.location.filterNotNull()
}
