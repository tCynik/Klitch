package ru.tcynik.meshtactics.data.marker.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.GeoMarkQueries
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.repository.CommandSender

class GeoMarkRepositoryImpl(
    private val commandSender: CommandSender,
    private val meshNetwork: MeshNetworkRepository,
    private val channelRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val adapter: GeoMarkWaypointAdapter,
    private val geoMarkQueries: GeoMarkQueries,
) : GeoMarkRepository {

    override fun observeGeoMarks(): Flow<List<GeoMarkModel>> =
        geoMarkQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { row -> row.toModel() } }

    override suspend fun sendGeoMark(mark: GeoMarkModel, contourId: ContourId?) {
        val ourNode = meshNetwork.observeOurNode().first()
        val ourNodeNum = ourNode?.num ?: 0
        val ourNodeId = ourNode?.nodeId ?: ""
        val nowSeconds = System.currentTimeMillis() / 1_000

        val packet = adapter.encode(mark, ourNodeNum, ourNodeId, nowSeconds)

        // Route to the selected contour's slot; default channel 0 if not specified or not found
        if (contourId != null) {
            val contour = channelRepository.observeContours().first()
                .find { it.id == contourId }
            val slot = contour?.transport?.meshtastic?.channelHash
                ?.let { hash -> channelSlotResolver.hashToSlot[hash] }
            if (slot != null) packet.channel = slot
        }
        commandSender.sendData(packet)

        val resolvedContourId = contourId?.value ?: resolveContourId(channelIndex = packet.channel)
        val expiresAt = mark.expiresAt ?: (nowSeconds + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS)
        geoMarkQueries.insert(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = adapter.encodePointsJson(mark.points),
            authorNodeId = ourNodeId,
            createdAt = nowSeconds,
            expiresAt = expiresAt,
            isSelf = 1L,
            logicalChannelId = resolvedContourId,
            color = mark.color.toLong(),
            name = mark.name,
            trackEndType = mark.trackEndType.ends.toLong(),
        )
    }

    override suspend fun persistReceived(mark: GeoMarkModel, contourId: ContourId) {
        geoMarkQueries.insertReceived(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = adapter.encodePointsJson(mark.points),
            authorNodeId = mark.authorNodeId,
            createdAt = mark.createdAt,
            expiresAt = mark.expiresAt,
            logicalChannelId = contourId.value,
            color = mark.color.toLong(),
            name = mark.name,
            trackEndType = mark.trackEndType.ends.toLong(),
        )
    }

    override suspend fun deleteExpired(nowSeconds: Long) {
        geoMarkQueries.deleteExpired(nowSeconds)
    }

    private suspend fun resolveContourId(channelIndex: Int): String {
        val hash = channelSlotResolver.slotToHash[channelIndex] ?: return ""
        return channelRepository.findByChannelHash(hash)?.id?.value ?: ""
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
        color = color.toInt(),
        name = name,
        trackEndType = TrackEndType.fromByte(track_end_type.toByte()),
    )
}
