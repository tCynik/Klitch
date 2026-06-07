package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.model.ContourNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

/**
 * Returns peer nodes, each paired with the resolved contour name.
 * Our own node is excluded.
 *
 * Channel-based contour filtering is intentionally absent — MeshPacket.channel reflects the
 * LOCAL index on the receiving radio and is unreliable for contour identification.
 * All decoded position packets already passed PSK verification at the hardware level.
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
            channelSlotResolver.mapsFlow,
        ) { (nodes, ourNode), contours, maps ->
            val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }
            nodes
                .filter { it.nodeId != ourNode?.nodeId }
                .map { node ->
                    val contourName = node.receivedOnSlot?.let { slot ->
                        maps.slotToHash[slot]?.let { hash -> contourByHash[hash]?.name }
                    }
                    ContourNodeModel(node = node, contourName = contourName)
                }
        }
}
