package ru.tcynik.meshtactics.data.marker.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.GeoMarkQueries
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class GeoMarkRepositoryImpl(
    private val packetRepository: PacketRepository,
    private val commandSender: CommandSender,
    private val meshNetwork: MeshNetworkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val geoMarkQueries: GeoMarkQueries,
) : GeoMarkRepository {

    /**
     * Combines received waypoints from the mesh layer (Room DB via PacketRepository)
     * with self-sent mark IDs from SQLDelight to tag [GeoMarkModel.isSelf] correctly.
     *
     * PacketRepository.getWaypoints() is the source-of-truth for all received marks —
     * it survives app restarts via the mesh layer's Room DB.
     */
    override fun observeGeoMarks(): Flow<List<GeoMarkModel>> {
        val selfIdsFlow: Flow<Set<String>> = geoMarkQueries.selectSelfIds()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.toSet() }

        return combine(
            packetRepository.getWaypoints(),
            selfIdsFlow,
        ) { packets, selfIds ->
            packets.mapNotNull { adapter.decode(it, selfIds) }
        }
    }

    override suspend fun sendGeoMark(mark: GeoMarkModel) {
        val ourNode = meshNetwork.observeOurNode().first()
        val ourNodeNum = ourNode?.num ?: 0
        val ourNodeId = ourNode?.nodeId ?: ""
        val nowSeconds = System.currentTimeMillis() / 1_000

        val packet = adapter.encode(mark, ourNodeNum, ourNodeId, nowSeconds)
        commandSender.sendData(packet)

        val pointsJson = buildPointsJson(mark)
        geoMarkQueries.insert(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = pointsJson,
            authorNodeId = ourNodeId,
            createdAt = nowSeconds,
            expiresAt = nowSeconds + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS,
            isSelf = 1L,
        )
    }

    /** Serialises points to a minimal JSON array without adding a kotlinx.serialization dependency. */
    private fun buildPointsJson(mark: GeoMarkModel): String {
        val items = mark.points.joinToString(",") { pt ->
            """{"lat":${pt.latitude},"lon":${pt.longitude}}"""
        }
        return "[$items]"
    }
}
