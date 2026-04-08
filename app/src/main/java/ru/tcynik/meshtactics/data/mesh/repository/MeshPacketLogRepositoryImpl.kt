package ru.tcynik.meshtactics.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.mesh.mapper.toMeshPacketLogModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.meshtactics.mesh.repository.MeshLogRepository as MeshLogRepo

class MeshPacketLogRepositoryImpl(
    private val meshLogRepository: MeshLogRepo,
) : MeshPacketLogRepository {

    override fun observePacketLog(maxItems: Int): Flow<List<MeshPacketLogModel>> =
        meshLogRepository.getAllLogs(maxItem = maxItems).map { logs ->
            logs.map { it.toMeshPacketLogModel() }
        }
}
