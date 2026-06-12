package ru.tcynik.klitch.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.data.mesh.mapper.toMeshNodeModel
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.mesh.repository.NodeRepository as MeshNodeRepository

class MeshNetworkRepositoryImpl(
    private val meshNodeRepository: MeshNodeRepository,
) : MeshNetworkRepository {

    override fun observeNodes(): Flow<List<MeshNodeModel>> =
        meshNodeRepository.getNodes().map { nodes -> nodes.map { it.toMeshNodeModel() } }

    override fun observeOurNode(): Flow<MeshNodeModel?> =
        meshNodeRepository.ourNodeInfo.map { node ->
            node?.toMeshNodeModel()
        }
}
