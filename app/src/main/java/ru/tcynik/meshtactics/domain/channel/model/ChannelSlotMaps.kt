package ru.tcynik.meshtactics.domain.channel.model

data class ChannelSlotMaps(
    val slotToHash: Map<Int, LogicalChannelHash> = emptyMap(),
    val hashToSlot: Map<LogicalChannelHash, Int> = emptyMap(),
)
