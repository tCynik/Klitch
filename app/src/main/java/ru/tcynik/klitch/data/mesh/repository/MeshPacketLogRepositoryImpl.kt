package ru.tcynik.klitch.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.data.mesh.mapper.toMeshPacketLogModel
import ru.tcynik.klitch.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.klitch.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.klitch.mesh.repository.MeshLogRepository as MeshLogRepo

class MeshPacketLogRepositoryImpl(
    private val meshLogRepository: MeshLogRepo,
) : MeshPacketLogRepository {

    override fun observePacketLog(maxItems: Int): Flow<List<MeshPacketLogModel>> =
        meshLogRepository.getAllLogs(maxItem = maxItems).map { logs ->
            logs.map { it.toMeshPacketLogModel() }
        }
}
