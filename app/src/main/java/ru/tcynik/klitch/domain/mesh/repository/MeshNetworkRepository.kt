package ru.tcynik.klitch.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel

interface MeshNetworkRepository {
    fun observeNodes(): Flow<List<MeshNodeModel>>
    fun observeOurNode(): Flow<MeshNodeModel?>
}
