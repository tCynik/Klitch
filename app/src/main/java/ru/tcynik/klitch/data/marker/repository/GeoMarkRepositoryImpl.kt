package ru.tcynik.klitch.data.marker.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.data.local.GeoMarkQueries
import ru.tcynik.klitch.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.TrackEndType
import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.mesh.repository.MeshRouter
import ru.tcynik.klitch.mesh.repository.PacketRepository

class GeoMarkRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val meshNetwork: MeshNetworkRepository,
    private val channelRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val adapter: GeoMarkWaypointAdapter,
    private val geoMarkQueries: GeoMarkQueries,
    private val packetRepository: PacketRepository,
    sendScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : GeoMarkRepository {

    private val sendQueue = GeoMarkSendQueue(
        scope = sendScope,
        minIntervalMs = MIN_SEND_INTERVAL_MS,
        sendBlock = { mark, contourId -> transmitMeshMark(mark, contourId) },
    )

    override fun observeGeoMarks(): Flow<List<GeoMarkModel>> =
        geoMarkQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { row -> row.toModel() } }

    override suspend fun sendGeoMark(mark: GeoMarkModel, contourId: ContourId?, localOnly: Boolean) {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val expiresAt = mark.expiresAt ?: (nowSeconds + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS)

        if (localOnly) {
            insertSelfMark(
                mark = mark,
                createdAt = nowSeconds,
                expiresAt = expiresAt,
                authorNodeId = "",
                logicalChannelId = "",
            )
            return
        }

        insertSelfMark(
            mark = mark,
            createdAt = nowSeconds,
            expiresAt = expiresAt,
            authorNodeId = "",
            logicalChannelId = contourId?.value.orEmpty(),
        )
        sendQueue.schedule(mark, contourId)
    }

    private suspend fun insertSelfMark(
        mark: GeoMarkModel,
        createdAt: Long,
        expiresAt: Long,
        authorNodeId: String,
        logicalChannelId: String,
    ) {
        geoMarkQueries.insert(
            id = mark.id,
            waypointId = mark.waypointId.toLong(),
            type = mark.type.name,
            pointsJson = adapter.encodePointsJson(mark.points),
            authorNodeId = authorNodeId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            isSelf = 1L,
            logicalChannelId = logicalChannelId,
            color = mark.color.toLong(),
            name = mark.name,
            trackEndType = mark.trackEndType.ends.toLong(),
            shape = mark.shape.ordinal.toLong(),
        )
    }

    private suspend fun transmitMeshMark(mark: GeoMarkModel, contourId: ContourId?) {
        val nowSeconds = System.currentTimeMillis() / 1_000

        val ourNode = meshNetwork.observeOurNode().first()
        val ourNodeNum = ourNode?.num ?: 0
        val ourNodeId = ourNode?.nodeId ?: ""

        val packet = adapter.encode(mark, ourNodeNum, ourNodeId, nowSeconds)

        if (contourId != null) {
            val contour = channelRepository.observeContours().first()
                .find { it.id == contourId }
            val slot = contour?.transport?.meshtastic?.channelHash
                ?.let { hash -> channelSlotResolver.hashToSlot[hash] }
            if (slot != null) packet.channel = slot
        }

        meshRouter.actionHandler.handleSend(packet, ourNodeNum)

        val resolvedContourId = contourId?.value ?: resolveContourId(channelIndex = packet.channel)
        if (resolvedContourId.isNotEmpty() && resolvedContourId != mark.logicalChannelId) {
            val expiresAt = mark.expiresAt
                ?: (nowSeconds + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS)
            geoMarkQueries.insert(
                id = mark.id,
                waypointId = mark.waypointId.toLong(),
                type = mark.type.name,
                pointsJson = adapter.encodePointsJson(mark.points),
                authorNodeId = ourNodeId,
                createdAt = mark.createdAt,
                expiresAt = expiresAt,
                isSelf = 1L,
                logicalChannelId = resolvedContourId,
                color = mark.color.toLong(),
                name = mark.name,
                trackEndType = mark.trackEndType.ends.toLong(),
                shape = mark.shape.ordinal.toLong(),
            )
        } else {
            geoMarkQueries.updateMeshSentAuthor(authorNodeId = ourNodeId, id = mark.id)
        }
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
            shape = mark.shape.ordinal.toLong(),
        )
    }

    override suspend fun toggleVisibility(id: String, visible: Boolean) {
        geoMarkQueries.setVisible(isVisible = if (visible) 1L else 0L, id = id)
    }

    override suspend fun updateExpiresAt(id: String, expiresAt: Long) {
        geoMarkQueries.updateExpiresAt(expiresAt = expiresAt, id = id)
    }

    override suspend fun deleteById(id: String) {
        val row = geoMarkQueries.selectById(id).executeAsOneOrNull()
        dismissMarkIds(id, row?.waypoint_id?.toInt())
        geoMarkQueries.deleteById(id)
        resolveWaypointIdForPacketCleanup(id, row?.waypoint_id?.toInt())?.let { waypointId ->
            packetRepository.deleteWaypoint(waypointId)
        }
        resolveMeshPacketIdForCleanup(id)?.let { packetId ->
            packetRepository.deleteWaypointByMeshPacketId(packetId)
        }
    }

    override suspend fun getActiveMarkIds(): Set<String> =
        geoMarkQueries.selectAllIds().executeAsList().toSet()

    override suspend fun getActiveWaypointIds(): Set<Int> =
        geoMarkQueries.selectActiveWaypointIds()
            .executeAsList()
            .map { it.toInt() }
            .toSet()

    override suspend fun getDismissedMarkIds(): Set<String> =
        geoMarkQueries.selectDismissedIds().executeAsList().toSet()

    override suspend fun deleteExpired(nowSeconds: Long) {
        geoMarkQueries.deleteExpired(nowSeconds)
    }

    private fun dismissMarkIds(markId: String, storedWaypointId: Int?) {
        geoMarkQueries.insertDismissed(markId)
        resolveWaypointIdForPacketCleanup(markId, storedWaypointId)?.let { waypointId ->
            geoMarkQueries.insertDismissed("$WP_ID_PREFIX$waypointId")
        }
    }

    private fun resolveWaypointIdForPacketCleanup(markId: String, storedWaypointId: Int?): Int? {
        storedWaypointId?.takeIf { it != 0 }?.let { return it }
        if (markId.startsWith(WP_ID_PREFIX)) {
            return markId.removePrefix(WP_ID_PREFIX).toIntOrNull()?.takeIf { it != 0 }
        }
        return null
    }

    private fun resolveMeshPacketIdForCleanup(markId: String): Int? {
        if (!markId.startsWith(PKT_ID_PREFIX)) return null
        return markId.removePrefix(PKT_ID_PREFIX).toIntOrNull()?.takeIf { it != 0 }
    }

    companion object {
        private const val WP_ID_PREFIX = "wp-"
        private const val PKT_ID_PREFIX = "pkt-"
        /**
         * Min gap between consecutive mesh geo-mark sends.
         * Meshtastic firmware rate-limits phone WAYPOINT_APP to one packet per 10 s (PhoneAPI);
         * shorter intervals are dropped on the radio — see meshtastic/firmware PR #6699.
         */
        const val MIN_SEND_INTERVAL_MS = 10_500L
    }

    private suspend fun resolveContourId(channelIndex: Int): String {
        val hash = channelSlotResolver.slotToHash[channelIndex] ?: return ""
        return channelRepository.findByChannelHash(hash)?.id?.value ?: ""
    }

    private fun ru.tcynik.klitch.data.local.Geo_mark.toModel() = GeoMarkModel(
        id = id,
        waypointId = waypoint_id.toInt(),
        type = GeoMarkType.valueOf(type),
        points = adapter.decodePointsJson(points_json),
        authorNodeId = author_node_id,
        createdAt = created_at,
        expiresAt = expires_at,
        isSelf = is_self == 1L,
        logicalChannelId = logical_channel_id,
        color = color.toInt(),
        name = name,
        trackEndType = TrackEndType.fromByte(track_end_type.toByte()),
        shape = GeoMarkShape.entries.getOrElse(shape.toInt()) { GeoMarkShape.CIRCLE },
        isVisible = is_visible == 1L,
    )
}
