package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.allowsDisplay
import ru.tcynik.meshtactics.domain.channel.model.contourOrNull
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.meshtactics.domain.mesh.model.ContourNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

/**
 * Returns peer nodes, each paired with the resolved contour name.
 * Our own node is excluded. Nodes are filtered by contour via [ResolveContourFromSlotUseCase].
 */
class ObserveContourNodesUseCase(
    private val repository: MeshNetworkRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val resolveContourFromSlot: ResolveContourFromSlotUseCase,
) : FlowUseCase<NoParams, List<ContourNodeModel>>() {

    override fun invoke(params: NoParams): Flow<List<ContourNodeModel>> =
        combine(
            combine(
                repository.observeNodes(),
                repository.observeOurNode(),
            ) { nodes, ourNode -> nodes to ourNode },
            contourRepository.observeContours(),
            contourRepository.observePrimaryContourId(),
            channelSlotResolver.mapsFlow,
            contourRepository.observeSosMode(),
        ) { (nodes, ourNode), contours, primaryId, maps, sosMode ->
            nodes
                .filter { it.nodeId != ourNode?.nodeId }
                .mapNotNull { node ->
                    val slot = node.receivedOnSlot ?: return@mapNotNull null
                    val resolution = resolveContourFromSlot(slot, contours, maps, primaryId, sosMode)
                    if (!resolution.allowsDisplay()) return@mapNotNull null
                    ContourNodeModel(
                        node = node,
                        contourName = resolution.contourOrNull()?.name,
                    )
                }
        }
}
