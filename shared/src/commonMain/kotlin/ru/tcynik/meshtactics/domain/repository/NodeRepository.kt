package ru.tcynik.meshtactics.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.model.NodeModel

interface NodeRepository {
    fun observeNodes(): Flow<List<NodeModel>>
    suspend fun refreshNodes()
    suspend fun connectToNode(nodeId: String)
    suspend fun disconnectFromNode(nodeId: String)
}
