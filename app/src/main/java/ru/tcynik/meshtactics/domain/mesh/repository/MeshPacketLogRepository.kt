package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshPacketLogModel

interface MeshPacketLogRepository {
    fun observePacketLog(maxItems: Int = 200): Flow<List<MeshPacketLogModel>>
}
