package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy

class GeoSendPolicyImpl(
    private val contourRepository: ContourRepository,
) : GeoSendPolicy {
    override fun observeAllowed(): Flow<Boolean> =
        contourRepository.observeEmergencyIsActive().map { emergencyActive -> !emergencyActive }
}
