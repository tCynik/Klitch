package ru.tcynik.meshtactics.domain.channel

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash

interface ChannelSlotResolver {
    val slotToHash: Map<Int, LogicalChannelHash>
    val hashToSlot: Map<LogicalChannelHash, Int>
    val mapsFlow: StateFlow<ChannelSlotMaps>
}
