package ru.tcynik.meshtactics.presentation.feature.settings.models

import ru.tcynik.meshtactics.domain.channel.model.ChannelSyncStatus
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId

data class ChannelItem(
    val id: LogicalChannelId,
    val name: String,
    val isAutoSync: Boolean,
    val syncStatus: ChannelSyncStatus,
)
