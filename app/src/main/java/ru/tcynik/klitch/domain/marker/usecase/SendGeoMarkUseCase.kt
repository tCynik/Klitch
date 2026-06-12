package ru.tcynik.klitch.domain.marker.usecase

import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

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
