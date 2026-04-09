package ru.tcynik.meshtactics.domain.map.usecase

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.mesh.common.util.latLongToMeter

private const val TAG = "NodeMarkers"
private const val MIN_SPEED_FOR_HEADING = 1

// Maximum age of a GPS position report to be considered fresh, in seconds.
// Positions older than this threshold are excluded from the map and the node counter.
// Adjust based on field experience — 2 hours matches the mesh online-status window.
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
 * A node's position is included only when [MeshNodeModel.positionTime] is within
 * [POSITION_FRESHNESS_SECONDS] of the current time. Nodes with valid coordinates but a stale
 * GPS timestamp (e.g. GPS disabled, node sending telemetry only) are excluded.
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
            val withFreshPosition = peers.filter {
                it.hasValidPosition && it.positionTime > freshnessThreshold
            }
            Log.d(TAG, "update: myPosition = '${ourNode?.latitude}/${ourNode?.longitude}', nodes=${nodes.size}/${peers.size} " +
                "freshCount=${withFreshPosition.size} " +
                "[${withFreshPosition.joinToString { it.toLogString(ourNode, nowSeconds) }}]")
            withFreshPosition.map { node ->
                NodeMarkerModel(
                    nodeId = node.nodeId,
                    longName = node.longName,
                    position = GeoPoint(node.latitude, node.longitude),
                    isOnline = node.isOnline,
                    heading = if (node.groundSpeed >= MIN_SPEED_FOR_HEADING) node.groundTrack.toFloat() else null,
                )
            }
        }
}

private fun MeshNodeModel.toLogString(ourNode: MeshNodeModel?, nowSeconds: Long): String {
    val ageStr = if (positionTime > 0) "${nowSeconds - positionTime}s ago" else "no time"
    val distStr = if (ourNode != null && ourNode.hasValidPosition && hasValidPosition) {
        val meters = latLongToMeter(latitude, longitude, ourNode.latitude, ourNode.longitude).toInt()
        if (meters >= 1000) "${"%.1f".format(meters / 1000.0)}km" else "${meters}m"
    } else "dist=?"
    val coordStr = "%.5f,%.5f".format(latitude, longitude)
    return "$longName($ageStr $distStr $coordStr)"
}
