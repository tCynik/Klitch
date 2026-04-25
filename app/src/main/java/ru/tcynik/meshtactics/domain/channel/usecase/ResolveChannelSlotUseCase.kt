package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot

sealed interface SlotResolution {
    data class AlreadySynced(val slot: Int) : SlotResolution
    data class FreeSlot(val slot: Int) : SlotResolution
    data object NoFreeSlot : SlotResolution
}

class ResolveChannelSlotUseCase {
    operator fun invoke(contour: Contour, nodeChannels: List<NodeChannelSlot>): SlotResolution {
        val contourHash = contour.transport.meshtastic.channelHash

        val matched = nodeChannels.find { slot ->
            slot.index != 0 && slot.isEnabled &&
                ContourHash.compute(slot.name, slot.psk) == contourHash
        }
        if (matched != null) return SlotResolution.AlreadySynced(matched.index)

        val freeSlot = nodeChannels.find { slot -> slot.index != 0 && !slot.isEnabled }
        if (freeSlot != null) return SlotResolution.FreeSlot(freeSlot.index)

        return SlotResolution.NoFreeSlot
    }
}
