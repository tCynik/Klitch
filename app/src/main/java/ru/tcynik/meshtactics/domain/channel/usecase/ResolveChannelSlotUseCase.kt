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
    operator fun invoke(
        contour: Contour,
        nodeChannels: List<NodeChannelSlot>,
        usedSlots: Set<Int> = emptySet(),
    ): SlotResolution {
        val contourHash = contour.transport.meshtastic.channelHash

        val matched = nodeChannels.find { slot ->
            slot.index != 0 && slot.index !in usedSlots && slot.isEnabled &&
                ContourHash.compute(slot.name, slot.psk) == contourHash
        }
        if (matched != null) {
            return if (matched.positionPrecision > 0) SlotResolution.AlreadySynced(matched.index)
            else SlotResolution.FreeSlot(matched.index)
        }

        val freeSlot = nodeChannels.find { slot ->
            slot.index != 0 && slot.index !in usedSlots && !slot.isEnabled
        }
        if (freeSlot != null) return SlotResolution.FreeSlot(freeSlot.index)

        val reportedIndices = nodeChannels.map { it.index }.toSet()
        val unconfiguredIndex = (1..7).firstOrNull { it !in reportedIndices && it !in usedSlots }
        if (unconfiguredIndex != null) return SlotResolution.FreeSlot(unconfiguredIndex)

        return SlotResolution.NoFreeSlot
    }
}
