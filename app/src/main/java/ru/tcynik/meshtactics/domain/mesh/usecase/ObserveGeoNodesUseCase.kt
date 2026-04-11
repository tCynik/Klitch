package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.mesh.model.GeoNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.mesh.common.util.latLongToMeter

/**
 * Returns peer nodes that have a valid GPS position, sorted by [GeoNodeModel.positionTime]
 * descending (most recently updated first). Our own node is excluded.
 *
 * Distance is null when our node has no valid position.
 */
class ObserveGeoNodesUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, List<GeoNodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<GeoNodeModel>> =
        combine(
            repository.observeNodes(),
            repository.observeOurNode(),
        ) { nodes, ourNode ->
            val ourNodeId = ourNode?.nodeId
            val ourHasPosition = ourNode?.hasValidPosition == true

            nodes
                .filter { it.nodeId != ourNodeId && it.hasValidPosition }
                .sortedByDescending { it.positionTime }
                .map { node ->
                    val distance = if (ourHasPosition && ourNode != null) {
                        latLongToMeter(
                            node.latitude, node.longitude,
                            ourNode.latitude, ourNode.longitude,
                        ).toInt()
                    } else null

                    GeoNodeModel(
                        nodeId = node.nodeId,
                        shortName = node.shortName,
                        distanceMeters = distance,
                        positionTime = node.positionTime,
                    )
                }
        }
}
