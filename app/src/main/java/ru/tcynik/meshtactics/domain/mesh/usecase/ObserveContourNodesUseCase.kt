package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.model.ContourNodeModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

/**
 * Returns peer nodes filtered by active contour, each paired with the resolved contour name.
 * Our own node is excluded.
 *
 * Filtering rules (same as [ObserveNodeMarkersUseCase] and [ObserveGeoNodesUseCase]):
 * - receivedOnSlot=null → show (fallback, no slot data)
 * - receivedOnSlot=0 → show (Primary channel, always visible)
 * - receivedOnSlot=1 → show only when SOS mode is active
 * - receivedOnSlot=N → show only when the corresponding contour is active
 *
 * [ContourNodeModel.contourName] is null when slot is unknown or has no matching contour.
 */
class ObserveContourNodesUseCase(
    private val repository: MeshNetworkRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) : FlowUseCase<NoParams, List<ContourNodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<ContourNodeModel>> =
        combine(
            combine(
                repository.observeNodes(),
                repository.observeOurNode(),
            ) { nodes, ourNode -> nodes to ourNode },
            contourRepository.observeContours(),
            contourRepository.observeSosMode(),
            channelSlotResolver.mapsFlow,
        ) { (nodes, ourNode), contours, sosMode, maps ->
            val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }
            nodes
                .filter { it.nodeId != ourNode?.nodeId }
                .filter { node -> passesContourFilter(node.receivedOnSlot, contourByHash, maps, sosMode) }
                .map { node ->
                    val contourName = node.receivedOnSlot?.let { slot ->
                        maps.slotToHash[slot]?.let { hash -> contourByHash[hash]?.name }
                    }
                    ContourNodeModel(node = node, contourName = contourName)
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
