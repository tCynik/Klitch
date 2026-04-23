package ru.tcynik.meshtactics.domain.channel.usecase

import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot

sealed interface SlotResolution {
    data class AlreadySynced(val slot: Int) : SlotResolution
    data class FreeSlot(val slot: Int) : SlotResolution
    data object NoFreeSlot : SlotResolution
}

class ResolveChannelSlotUseCase {
    operator fun invoke(channel: LogicalChannel, nodeChannels: List<NodeChannelSlot>): SlotResolution {
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
            ?: return SlotResolution.NoFreeSlot

        val matched = nodeChannels.find { slot ->
            slot.index != 0 &&
                slot.isEnabled &&
                slot.name == channel.metadata.name &&
                slot.psk.contentEquals(binding.psk)
        }
        if (matched != null) return SlotResolution.AlreadySynced(matched.index)

        val freeSlot = nodeChannels.find { slot -> slot.index != 0 && !slot.isEnabled }
        if (freeSlot != null) return SlotResolution.FreeSlot(freeSlot.index)

        return SlotResolution.NoFreeSlot
    }
}
