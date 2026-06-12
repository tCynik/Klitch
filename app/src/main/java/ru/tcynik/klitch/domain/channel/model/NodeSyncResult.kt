package ru.tcynik.klitch.domain.channel.model

sealed interface NodeSyncResult {
    data object InSync : NodeSyncResult
    data object NeedsSync : NodeSyncResult
}
