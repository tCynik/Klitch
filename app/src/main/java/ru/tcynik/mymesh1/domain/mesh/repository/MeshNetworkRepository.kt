package ru.tcynik.mymesh1.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshNodeModel

interface MeshNetworkRepository {
    fun observeNodes(): Flow<List<MeshNodeModel>>
    fun observeOurNode(): Flow<MeshNodeModel?>
}
