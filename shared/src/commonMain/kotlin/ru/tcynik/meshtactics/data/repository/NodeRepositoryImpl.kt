package ru.tcynik.meshtactics.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
// NodeQueries — генерируется SQLDelight из Node.sq после первого билда
import ru.tcynik.meshtactics.data.local.NodeQueries
import ru.tcynik.meshtactics.data.local.mapper.toDomain
import ru.tcynik.meshtactics.data.remote.api.MeshApiService
import ru.tcynik.meshtactics.data.settings.AppSettings
import ru.tcynik.meshtactics.domain.model.NodeModel
import ru.tcynik.meshtactics.domain.repository.NodeRepository

class NodeRepositoryImpl(
    private val apiService: MeshApiService,
    private val nodeQueries: NodeQueries,
    private val settings: AppSettings,
) : NodeRepository {

    override fun observeNodes(): Flow<List<NodeModel>> =
        nodeQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshNodes() {
        val nodes = apiService.getNodes()
        nodeQueries.transaction {
            nodes.forEach { dto ->
                nodeQueries.insertOrReplace(
                    id = dto.id,
                    name = dto.name,
                    address = dto.address,
                    rssi = dto.rssi.toLong(),
                    isConnected = if (dto.isConnected) 1L else 0L,
                    lastSeen = dto.lastSeen,
                )
            }
        }
        settings.setLastSync(Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun connectToNode(nodeId: String) {
        // BLE/WiFi Direct — реализуется в androidMain через платформенный сервис
    }

    override suspend fun disconnectFromNode(nodeId: String) {
        // BLE/WiFi Direct — реализуется в androidMain через платформенный сервис
    }
}
