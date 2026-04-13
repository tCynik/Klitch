package ru.tcynik.meshtactics.domain.map.usecase

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
private const val MIN_SPEED_FOR_HEADING = 1

// Maximum age of a GPS position report to be considered fresh, in seconds.
// Positions fresher than this threshold are shown with normal colors;
// older positions are shown as grey (stale) markers.
// 2 minutes — threshold for fresh vs stale visual distinction.
private const val POSITION_FRESHNESS_SECONDS = 2 * 60

/** Maximum age of a GPS position to be displayed at all, in seconds. Positions older than this are hidden. */
private const val MAX_POSITION_AGE_SECONDS = 12 * 60 * 60 // 12 hours

/** How often to re-evaluate stale status, in milliseconds. */
private const val STALE_CHECK_INTERVAL_MS = 10_000L

/**
 * Returns map markers for **peer nodes only** — our own node is intentionally excluded.
 *
 * Design decision: our radio device is part of the user's equipment, not a mesh peer.
 * The user's position is already shown on the map via the GPS location layer (CircleLayer).
 * Including our node here would create visual duplication — two markers at the same location.
 *
 * Any future use case that lists or counts peer nodes must apply the same exclusion.
 *
 * Nodes with valid position are always shown. Fresh nodes (position within
 * [POSITION_FRESHNESS_SECONDS]) are shown with normal colors. Stale nodes (older position)
 * are shown as grey markers via the [NodeMarkerModel.isStale] flag.
 *
 * Nodes with position older than [MAX_POSITION_AGE_SECONDS] are filtered out and not displayed.
 *
 * The stale status is re-evaluated periodically ([STALE_CHECK_INTERVAL_MS]) so that nodes
 * transition from fresh to stale dynamically while the app is running, not just on restart.
 */
class ObserveNodeMarkersUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, List<NodeMarkerModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeMarkerModel>> =
        combine(
            repository.observeNodes(),
            repository.observeOurNode(),
            staleTicker(),
        ) { nodes, ourNode, _ ->
            buildMarkerList(nodes, ourNode)
        }

    /** Periodic ticker that emits at [STALE_CHECK_INTERVAL_MS] intervals to trigger stale re-evaluation. */
    private fun staleTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(STALE_CHECK_INTERVAL_MS)
        }
    }

    private fun buildMarkerList(
        nodes: List<MeshNodeModel>,
        ourNode: MeshNodeModel?,
    ): List<NodeMarkerModel> {
        val ourNodeId = ourNode?.nodeId
        val nowSeconds = System.currentTimeMillis() / 1000
        val freshnessThreshold = nowSeconds - POSITION_FRESHNESS_SECONDS
        val maxAgeThreshold = nowSeconds - MAX_POSITION_AGE_SECONDS

        val peers = nodes.filter { it.nodeId != ourNodeId }
        val withPosition = peers.filter { it.hasValidPosition }
        
        // Filter out nodes with position older than MAX_POSITION_AGE_SECONDS
        val recentEnough = withPosition.filter { node ->
            val effectiveTime = if (node.positionTime > 0) node.positionTime else node.lastHeard
            effectiveTime > maxAgeThreshold
        }
        
        val freshCount = recentEnough.count {
            val effectiveTime = if (it.positionTime > 0) it.positionTime else it.lastHeard
            effectiveTime > freshnessThreshold
        }
        Logger.d { "update: myPosition = '${ourNode?.latitude}/${ourNode?.longitude}', nodes=${nodes.size}/${peers.size} " +
                "withPosition=${withPosition.size} recent=${recentEnough.size} fresh=$freshCount " +
                "[${recentEnough.joinToString { it.toLogString(nowSeconds) }}]" }
        return recentEnough.map { node ->
            val effectiveTime = if (node.positionTime > 0) node.positionTime else node.lastHeard
            val isStale = effectiveTime <= freshnessThreshold
            NodeMarkerModel(
                nodeId = node.nodeId,
                longName = node.longName,
                position = GeoPoint(node.latitude, node.longitude),
                isOnline = node.isOnline,
                isStale = isStale,
                heading = if (node.groundSpeed >= MIN_SPEED_FOR_HEADING) node.groundTrack.toFloat() else null,
            )
        }
    }
}

private fun MeshNodeModel.toLogString(nowSeconds: Long): String {
    val ageStr = if (positionTime > 0) "${nowSeconds - positionTime}s ago" else "no time"
    val coordStr = "%.5f,%.5f".format(latitude, longitude)
    return "$longName($ageStr $coordStr)"
}
