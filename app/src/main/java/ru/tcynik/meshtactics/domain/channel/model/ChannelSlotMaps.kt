package ru.tcynik.meshtactics.domain.channel.model

data class ChannelSlotMaps(
    val slotToHash: Map<Int, ContourHash> = emptyMap(),
    val hashToSlot: Map<ContourHash, Int> = emptyMap(),
)
