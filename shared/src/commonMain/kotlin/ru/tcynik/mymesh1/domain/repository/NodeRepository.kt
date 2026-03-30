package ru.tcynik.mymesh1.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.model.NodeModel

interface NodeRepository {
    fun observeNodes(): Flow<List<NodeModel>>
    suspend fun refreshNodes()
    suspend fun connectToNode(nodeId: String)
    suspend fun disconnectFromNode(nodeId: String)
}
