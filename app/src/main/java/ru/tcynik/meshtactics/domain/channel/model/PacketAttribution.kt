package ru.tcynik.meshtactics.domain.channel.model

data class PacketAttribution(
    val slot: TransportSlot,
    val senderNodeId: String,
    val contourId: ContourId?,
    val resolution: ContourResolution,
)
