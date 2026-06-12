package ru.tcynik.klitch.data.mesh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.mesh.repository.GeoSendPolicy

class GeoSendPolicyImpl(
    private val contourRepository: ContourRepository,
) : GeoSendPolicy {
    override fun observeAllowed(): Flow<Boolean> =
        contourRepository.observeSosMode().map { sosActive -> !sosActive }
}
