package ru.tcynik.klitch.domain.channel.usecase

import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourResolution
import ru.tcynik.klitch.domain.channel.model.isEmergency

class ResolveContourFromSlotUseCase {
    operator fun invoke(
        slot: Int,
        contours: List<Contour>,
        maps: ChannelSlotMaps,
        primaryContourId: ContourId,
        sosMode: Boolean,
    ): ContourResolution = when (slot) {
        0 -> contours.find { it.id == primaryContourId }
            ?.let { ContourResolution.Deliver(it) }
            ?: ContourResolution.Drop("primary contour not found")

        1 -> contours.find { it.isEmergency }?.let { emergency ->
            if (sosMode) ContourResolution.Deliver(emergency)
            else ContourResolution.SilentStore(emergency)
        } ?: ContourResolution.Drop("emergency contour not found")

        PKC_SLOT -> ContourResolution.Drop("PKC slot not supported")

        else -> {
            val hash = maps.slotToHash[slot]
                ?: return ContourResolution.Drop("unknown slot $slot")
            val contour = contours.find { it.transport.meshtastic.channelHash == hash }
                ?: return ContourResolution.Drop("no contour for hash $hash")
            if (!contour.isActive) ContourResolution.Drop("contour inactive: ${contour.name}")
            else ContourResolution.Deliver(contour)
        }
    }

    private companion object {
        const val PKC_SLOT = 8
    }
}
