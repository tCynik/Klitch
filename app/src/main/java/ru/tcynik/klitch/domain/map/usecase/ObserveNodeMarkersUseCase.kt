package ru.tcynik.klitch.domain.map.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.allowsDisplay
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.domain.marker.model.NodeMarkerModel
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

private const val MIN_SPEED_FOR_HEADING = 1

// Threshold for fresh vs stale: 3 × stationary broadcast interval (180 s) = 540 s.
// A node missed by 3 consecutive expected broadcasts is considered stale.
// Sync with AndroidMeshLocationManager.STATIONARY_INTERVAL_MS when tuning.
private const val POSITION_FRESHNESS_SECONDS = 3 * 180

/** Maximum age of a GPS position to be displayed at all, in seconds. Positions older than this are hidden. */
private const val MAX_POSITION_AGE_SECONDS = 12 * 60 * 60 // 12 hours

/** How often to re-evaluate stale status, in milliseconds. */
private const val STALE_CHECK_INTERVAL_MS = 10_000L

/**
 * Returns map markers for peer nodes only — our own node is intentionally excluded.
 *
 * Visibility requires a valid position within [MAX_POSITION_AGE_SECONDS] and a contour filter
 * pass via [ResolveContourFromSlotUseCase] on [MeshNodeModel.receivedOnSlot].
 */
class ObserveNodeMarkersUseCase(
    private val repository: MeshNetworkRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val resolveContourFromSlot: ResolveContourFromSlotUseCase,
    private val logger: Logger,
) : FlowUseCase<NoParams, List<NodeMarkerModel>>() {

    private data class PrevStatus(val isOnline: Boolean, val isStale: Boolean)
    private val previousStatus = mutableMapOf<String, PrevStatus>()

    override fun invoke(params: NoParams): Flow<List<NodeMarkerModel>> =
        combine(
            combine(
                repository.observeNodes(),
                repository.observeOurNode(),
                contourRepository.observeContours(),
                contourRepository.observePrimaryContourId(),
                channelSlotResolver.mapsFlow,
            ) { nodes, ourNode, contours, primaryId, maps ->
                NodeFilterContext(nodes, ourNode, contours, primaryId, maps)
            },
            contourRepository.observeSosMode(),
            staleTicker(),
        ) { context, sosMode, _ ->
            buildMarkerList(
                context.nodes,
                context.ourNode,
                context.contours,
                context.primaryId,
                context.maps,
                sosMode,
            )
        }

    private data class NodeFilterContext(
        val nodes: List<MeshNodeModel>,
        val ourNode: MeshNodeModel?,
        val contours: List<Contour>,
        val primaryId: ContourId,
        val maps: ChannelSlotMaps,
    )

    private fun staleTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(STALE_CHECK_INTERVAL_MS)
        }
    }

    private fun buildMarkerList(
        nodes: List<MeshNodeModel>,
        ourNode: MeshNodeModel?,
        contours: List<Contour>,
        primaryId: ContourId,
        maps: ChannelSlotMaps,
        sosMode: Boolean,
    ): List<NodeMarkerModel> {
        val ourNodeId = ourNode?.nodeId
        val nowSeconds = System.currentTimeMillis() / 1000
        val freshnessThreshold = nowSeconds - POSITION_FRESHNESS_SECONDS
        val maxAgeThreshold = nowSeconds - MAX_POSITION_AGE_SECONDS

        val peers = nodes.filter { it.nodeId != ourNodeId }
        val withPosition = peers.filter { it.hasValidPosition }
        val contourVisible = withPosition.filter { node ->
            if (sosMode) return@filter true
            val slot = node.receivedOnSlot ?: return@filter false
            resolveContourFromSlot(slot, contours, maps, primaryId, sosMode).allowsDisplay()
        }
        val visible = contourVisible.filter { it.positionTime > maxAgeThreshold }

        val freshCount = visible.count { it.positionTime > freshnessThreshold }
        logger.d("Node", "ObserveNodeMarkersUseCase: myPos=${ourNode?.latitude}/${ourNode?.longitude} " +
                "nodesSize=${nodes.size}/${peers.size} withPos=${withPosition.size} " +
                "visible=${visible.size} fresh=$freshCount")

        return visible.map { node ->
            val isStale = node.positionTime <= freshnessThreshold
            val marker = NodeMarkerModel(
                nodeId = node.nodeId,
                longName = node.longName,
                position = GeoPoint(node.latitude, node.longitude),
                isOnline = node.isOnline,
                isStale = isStale,
                heading = if (node.groundSpeed >= MIN_SPEED_FOR_HEADING) node.groundTrack.toFloat() else null,
            )
            val prev = previousStatus[node.nodeId]
            if (prev != null) {
                if (prev.isOnline != marker.isOnline) {
                    val lastHeardAgeS = nowSeconds - node.lastHeard
                    logger.d("Node", "${node.longName} online ${prev.isOnline}→${marker.isOnline}: lastHeard=${lastHeardAgeS}s ago")
                }
                if (prev.isStale != marker.isStale) {
                    val ageS = nowSeconds - node.positionTime
                    logger.d("Node", "${node.longName} stale ${prev.isStale}→${marker.isStale}: positionTime=${ageS}s ago (threshold=${POSITION_FRESHNESS_SECONDS}s)")
                }
            }
            previousStatus[node.nodeId] = PrevStatus(marker.isOnline, marker.isStale)
            marker
        }
    }
}

private fun MeshNodeModel.toLogString(nowSeconds: Long): String {
    val ageStr = if (positionTime > 0) "${nowSeconds - positionTime}s ago" else "no time"
    val coordStr = "%.5f,%.5f".format(latitude, longitude)
    return "$longName($ageStr $coordStr)"
}
