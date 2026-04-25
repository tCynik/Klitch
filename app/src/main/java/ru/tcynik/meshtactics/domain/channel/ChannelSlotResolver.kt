package ru.tcynik.meshtactics.domain.channel

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.ContourHash

interface ChannelSlotResolver {
    val slotToHash: Map<Int, ContourHash>
    val hashToSlot: Map<ContourHash, Int>
    val mapsFlow: StateFlow<ChannelSlotMaps>
}
