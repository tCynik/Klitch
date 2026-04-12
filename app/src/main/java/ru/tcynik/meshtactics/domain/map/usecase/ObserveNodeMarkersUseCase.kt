package ru.tcynik.meshtactics.domain.map.usecase

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 */
class ObserveNodeMarkersUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, List<NodeMarkerModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeMarkerModel>> =
        combine(
            repository.observeNodes(),
            repository.observeOurNode(),
        ) { nodes, ourNode ->
            val ourNodeId = ourNode?.nodeId
            val nowSeconds = System.currentTimeMillis() / 1000
            val freshnessThreshold = nowSeconds - POSITION_FRESHNESS_SECONDS

            val peers = nodes.filter { it.nodeId != ourNodeId }
            val withPosition = peers.filter { it.hasValidPosition }
            val freshCount = withPosition.count {
                val effectiveTime = if (it.positionTime > 0) it.positionTime else it.lastHeard
                effectiveTime > freshnessThreshold
            }
            Logger.d { "update: myPosition = '${ourNode?.latitude}/${ourNode?.longitude}', nodes=${nodes.size}/${peers.size} " +
                "withPosition=${withPosition.size} fresh=$freshCount " +
                "[${withPosition.joinToString { it.toLogString(nowSeconds) }}]" }
            withPosition.map { node ->
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
