package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class NodeProvisioningUseCase(
    private val contourRepository: ContourRepository,
    private val observeContours: ObserveContoursUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val logger: Logger,
) {
    suspend fun provision() {
        logger.d("Node", "provision() started")
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()

        val usedSlots = mutableSetOf(0, 1)
        contours.forEach { contour ->
            if (contour.isEmergency) return@forEach
            if (contour.id == primaryId) return@forEach
            when (val r = resolveSlot(contour, nodeChannels, usedSlots)) {
                is SlotResolution.AlreadySynced -> {
                    logger.d("Node", "  skip '${contour.name}' — already synced at slot ${r.slot}")
                    usedSlots.add(r.slot)
                }
                is SlotResolution.FreeSlot -> {
                    logger.d("Node", "  writeChannel slot=${r.slot} name='${meshtasticChannelName(contour)}'")
                    writeChannel(r.slot, meshtasticChannelName(contour), contour.transport.meshtastic.psk)
                    usedSlots.add(r.slot)
                }
                is SlotResolution.NoFreeSlot -> logger.w("Node", "  no free slots for '${contour.name}' — skipping")
            }
        }
        logger.d("Node", "provision() complete")
    }
}
