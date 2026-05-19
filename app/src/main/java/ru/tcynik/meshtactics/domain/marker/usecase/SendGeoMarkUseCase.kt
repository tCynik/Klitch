package ru.tcynik.meshtactics.domain.marker.usecase

import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

data class SendGeoMarkParams(
    val mark: GeoMarkModel,
    val contourId: ContourId? = null,
    val localOnly: Boolean = false,
)

class SendGeoMarkUseCase(
    private val repository: GeoMarkRepository,
) : UseCase<SendGeoMarkParams, Unit>() {
    override suspend fun invoke(params: SendGeoMarkParams) {
        repository.sendGeoMark(params.mark, params.contourId, params.localOnly)
    }
}
