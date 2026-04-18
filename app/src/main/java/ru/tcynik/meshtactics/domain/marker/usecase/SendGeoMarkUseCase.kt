package ru.tcynik.meshtactics.domain.marker.usecase

import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class SendGeoMarkUseCase(
    private val repository: GeoMarkRepository,
) : UseCase<GeoMarkModel, Unit>() {
    override suspend fun invoke(params: GeoMarkModel) {
        repository.sendGeoMark(params)
    }
}
