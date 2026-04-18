package ru.tcynik.meshtactics.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveGeoMarksUseCase(
    private val repository: GeoMarkRepository,
) : FlowUseCase<NoParams, List<GeoMarkModel>>() {
    override fun invoke(params: NoParams): Flow<List<GeoMarkModel>> =
        repository.observeGeoMarks()
}
