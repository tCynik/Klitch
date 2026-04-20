package ru.tcynik.meshtactics.presentation.feature.settings.models

import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId

data class ChannelItem(
    val id: LogicalChannelId,
    val name: String,
    val transportLabel: String,
)
