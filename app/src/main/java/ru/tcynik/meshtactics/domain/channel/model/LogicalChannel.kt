package ru.tcynik.meshtactics.domain.channel.model

data class LogicalChannel(
    val id: LogicalChannelId,
    val metadata: ChannelMetadata,
    val transports: List<TransportBinding>,
    val isAutoSync: Boolean = false,
)
