package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.model.GeoNodeModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.mesh.common.util.latLongToMeter

/**
 * Returns peer nodes that have a valid GPS position, sorted by [GeoNodeModel.positionTime]
 * descending (most recently updated first). Our own node is excluded.
 *
 * Distance is null when our node has no valid position.
 *
 * Nodes are additionally filtered by contour: a node received on slot 1 (Emergency) is hidden
 * outside SOS mode. Nodes on inactive contour slots are hidden. Null slot = show (fallback).
 */
class ObserveGeoNodesUseCase(
    private val repository: MeshNetworkRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) : FlowUseCase<NoParams, List<GeoNodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<GeoNodeModel>> =
        combine(
            combine(
                repository.observeNodes(),
                repository.observeOurNode(),
            ) { nodes, ourNode -> nodes to ourNode },
            contourRepository.observeContours(),
            contourRepository.observeSosMode(),
            channelSlotResolver.mapsFlow,
        ) { (nodes, ourNode), contours, sosMode, maps ->
            buildGeoNodes(nodes, ourNode, contours, maps, sosMode)
        }

    private fun buildGeoNodes(
        nodes: List<MeshNodeModel>,
        ourNode: MeshNodeModel?,
        contours: List<Contour>,
        maps: ChannelSlotMaps,
        sosMode: Boolean,
    ): List<GeoNodeModel> {
        val ourNodeId = ourNode?.nodeId
        val ourHasPosition = ourNode?.hasValidPosition == true
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }

        return nodes
            .filter { it.nodeId != ourNodeId && it.hasValidPosition }
            .filter { node -> passesContourFilter(node.receivedOnSlot, contourByHash, maps, sosMode) }
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

    private fun passesContourFilter(
        receivedOnSlot: Int?,
        contourByHash: Map<*, Contour>,
        maps: ChannelSlotMaps,
        sosMode: Boolean,
    ): Boolean = when (receivedOnSlot) {
        null -> true
        0 -> true
        1 -> sosMode
        else -> {
            val hash = maps.slotToHash[receivedOnSlot] ?: return true
            contourByHash[hash]?.isActive ?: true
        }
    }
}
