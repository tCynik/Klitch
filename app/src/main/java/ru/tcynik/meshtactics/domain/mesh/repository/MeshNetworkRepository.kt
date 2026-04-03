package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel

interface MeshNetworkRepository {
    fun observeNodes(): Flow<List<MeshNodeModel>>
    fun observeOurNode(): Flow<MeshNodeModel?>
}
