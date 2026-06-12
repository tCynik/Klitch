package ru.tcynik.klitch.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveGeoMarksUseCase(
    private val repository: GeoMarkRepository,
) : FlowUseCase<NoParams, List<GeoMarkModel>>() {
    override fun invoke(params: NoParams): Flow<List<GeoMarkModel>> =
        repository.observeGeoMarks()
}
