package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy

class GeoSendPolicyImpl : GeoSendPolicy {
    override fun observeAllowed(): Flow<Boolean> = flowOf(true)
}
