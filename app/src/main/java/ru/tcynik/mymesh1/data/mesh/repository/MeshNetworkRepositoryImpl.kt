package ru.tcynik.mymesh1.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.mymesh1.data.mesh.mapper.toMeshNodeModel
import ru.tcynik.mymesh1.domain.mesh.model.MeshNodeModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.mymesh1.mesh.repository.NodeRepository as MeshNodeRepository

class MeshNetworkRepositoryImpl(
    private val meshNodeRepository: MeshNodeRepository,
) : MeshNetworkRepository {

    override fun observeNodes(): Flow<List<MeshNodeModel>> =
        meshNodeRepository.getNodes().map { nodes ->
            nodes.map { it.toMeshNodeModel() }
        }

    override fun observeOurNode(): Flow<MeshNodeModel?> =
        meshNodeRepository.ourNodeInfo.map { node ->
            node?.toMeshNodeModel()
        }
}
