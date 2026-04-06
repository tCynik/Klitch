package ru.tcynik.meshtactics.domain.map.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNodeMarkersUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, List<NodeMarkerModel>>() {

    override fun invoke(params: NoParams): Flow<List<NodeMarkerModel>> =
        combine(
            repository.observeNodes(),
            repository.observeOurNode(),
        ) { nodes, ourNode ->
            val ourNodeId = ourNode?.nodeId
            // Guard: observeNodes() may or may not include ourNode in the Meshtastic protocol.
            // Ensure our node appears on the map if it has a valid position.
            val allNodes = if (ourNode != null && nodes.none { it.nodeId == ourNodeId }) {
                nodes + ourNode
            } else {
                nodes
            }
            allNodes
                .filter { it.hasValidPosition }
                .map { node ->
                    NodeMarkerModel(
                        nodeId = node.nodeId,
                        longName = node.longName,
                        position = GeoPoint(node.latitude, node.longitude),
                        isOurNode = node.nodeId == ourNodeId,
                    )
                }
        }
}
