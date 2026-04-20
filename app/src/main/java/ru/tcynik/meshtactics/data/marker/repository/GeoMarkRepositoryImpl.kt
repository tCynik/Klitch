package ru.tcynik.meshtactics.data.marker.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.GeoMarkQueries
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.repository.CommandSender

class GeoMarkRepositoryImpl(
    private val commandSender: CommandSender,
    private val meshNetwork: MeshNetworkRepository,
    private val channelRepository: LogicalChannelRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val geoMarkQueries: GeoMarkQueries,
) : GeoMarkRepository {

    override fun observeGeoMarks(): Flow<List<GeoMarkModel>> =
        geoMarkQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { row -> row.toModel() } }

    override suspend fun sendGeoMark(mark: GeoMarkModel) {
        val ourNode = meshNetwork.observeOurNode().first()
        val ourNodeNum = ourNode?.num ?: 0
        val ourNodeId = ourNode?.nodeId ?: ""
        val nowSeconds = System.currentTimeMillis() / 1_000

        val packet = adapter.encode(mark, ourNodeNum, ourNodeId, nowSeconds)
        commandSender.sendData(packet)

        val logicalChannelId = resolveChannelId(channelIndex = packet.channel)
        geoMarkQueries.insert(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = adapter.encodePointsJson(mark.points),
            authorNodeId = ourNodeId,
            createdAt = nowSeconds,
            expiresAt = nowSeconds + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS,
            isSelf = 1L,
            logicalChannelId = logicalChannelId,
        )
    }

    override suspend fun persistReceived(mark: GeoMarkModel, logicalChannelId: LogicalChannelId) {
        geoMarkQueries.insertReceived(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = adapter.encodePointsJson(mark.points),
            authorNodeId = mark.authorNodeId,
            createdAt = mark.createdAt,
            expiresAt = mark.expiresAt,
            logicalChannelId = logicalChannelId.value,
        )
    }

    override suspend fun deleteExpired(nowSeconds: Long) {
        geoMarkQueries.deleteExpired(nowSeconds)
    }

    private suspend fun resolveChannelId(channelIndex: Int): String {
        return channelRepository.observeChannels().first()
            .firstOrNull { ch ->
                ch.transports.filterIsInstance<MeshtasticBinding>()
                    .any { it.channelIndex == channelIndex }
            }?.id?.value ?: ""
    }

    private fun ru.tcynik.meshtactics.data.local.Geo_mark.toModel() = GeoMarkModel(
        id = id,
        waypointId = waypoint_id.toInt(),
        type = GeoMarkType.valueOf(type),
        points = adapter.decodePointsJson(points_json),
        authorNodeId = author_node_id,
        createdAt = created_at,
        expiresAt = expires_at,
        isSelf = is_self == 1L,
    )
}
