package ru.tcynik.klitch.domain.gps.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveGpsLocationUseCase(
    private val gpsRepository: GpsRepository,
) : FlowUseCase<NoParams, GpsLocation?>() {
    override fun invoke(params: NoParams): Flow<GpsLocation?> = gpsRepository.location
}
