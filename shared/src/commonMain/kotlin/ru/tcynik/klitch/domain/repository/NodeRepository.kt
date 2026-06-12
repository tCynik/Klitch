package ru.tcynik.klitch.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.model.NodeModel

interface NodeRepository {
    fun observeNodes(): Flow<List<NodeModel>>
    suspend fun refreshNodes()
    suspend fun connectToNode(nodeId: String)
    suspend fun disconnectFromNode(nodeId: String)
}
