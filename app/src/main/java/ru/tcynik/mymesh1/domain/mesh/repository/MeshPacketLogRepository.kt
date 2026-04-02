package ru.tcynik.mymesh1.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshPacketLogModel

interface MeshPacketLogRepository {
    fun observePacketLog(maxItems: Int = 200): Flow<List<MeshPacketLogModel>>
}
