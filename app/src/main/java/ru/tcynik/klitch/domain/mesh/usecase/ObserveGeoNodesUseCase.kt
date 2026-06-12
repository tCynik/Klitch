package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.allowsDisplay
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.klitch.domain.mesh.model.GeoNodeModel
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.mesh.common.util.latLongToMeter

/**
 * Returns peer nodes that have a valid GPS position, sorted by positionTime descending.
 * Our own node is excluded. Distance is null when our node has no valid position.
 */
class ObserveGeoNodesUseCase(
    private val repository: MeshNetworkRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val resolveContourFromSlot: ResolveContourFromSlotUseCase,
) : FlowUseCase<NoParams, List<GeoNodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<GeoNodeModel>> =
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
        ) { context, sosMode ->
            val nodes = context.nodes
            val ourNode = context.ourNode
            val contours = context.contours
            val primaryId = context.primaryId
            val maps = context.maps
            val ourNodeId = ourNode?.nodeId
            val ourHasPosition = ourNode?.hasValidPosition == true

            nodes
                .filter { it.nodeId != ourNodeId && it.hasValidPosition }
                .filter { node ->
                    if (sosMode) return@filter true
                    val slot = node.receivedOnSlot ?: return@filter false
                    resolveContourFromSlot(slot, contours, maps, primaryId, sosMode).allowsDisplay()
                }
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
                        groundSpeed = node.groundSpeed,
                        groundTrack = node.groundTrack,
                    )
                }
        }

    private data class NodeFilterContext(
        val nodes: List<MeshNodeModel>,
        val ourNode: MeshNodeModel?,
        val contours: List<Contour>,
        val primaryId: ContourId,
        val maps: ChannelSlotMaps,
    )
}
