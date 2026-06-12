package ru.tcynik.klitch.domain.channel.model

sealed interface ChannelSyncStatus {
    data object NotConnected : ChannelSyncStatus
    data class OnNode(val slot: Int) : ChannelSyncStatus
    data object NotOnNode : ChannelSyncStatus
    data object NoFreeSlot : ChannelSyncStatus
}
