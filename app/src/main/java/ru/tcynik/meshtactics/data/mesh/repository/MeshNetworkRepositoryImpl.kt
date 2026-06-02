package ru.tcynik.meshtactics.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.mesh.mapper.toMeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.data.NodePositionSlotCache
import ru.tcynik.meshtactics.mesh.repository.NodeRepository as MeshNodeRepository

class MeshNetworkRepositoryImpl(
    private val meshNodeRepository: MeshNodeRepository,
    private val nodePositionSlotCache: NodePositionSlotCache,
) : MeshNetworkRepository {

    override fun observeNodes(): Flow<List<MeshNodeModel>> =
        combine(
            meshNodeRepository.getNodes(),
            nodePositionSlotCache.slots,
        ) { nodes, slotMap ->
            nodes.map { it.toMeshNodeModel(slotMap[it.num]) }
        }

    override fun observeOurNode(): Flow<MeshNodeModel?> =
        meshNodeRepository.ourNodeInfo.map { node ->
            node?.toMeshNodeModel()
        }
}
