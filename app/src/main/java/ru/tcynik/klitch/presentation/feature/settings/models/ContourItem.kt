package ru.tcynik.klitch.presentation.feature.settings.models

import ru.tcynik.klitch.domain.channel.model.ChannelSyncStatus
import ru.tcynik.klitch.domain.channel.model.ContourId
import java.time.Instant

data class ContourItem(
    val id: ContourId,
    val name: String,
    val description: String?,
    val expiration: Instant?,
    val exclusivityTime: Instant?,
    val isActive: Boolean,
    val isEmergency: Boolean,
    val isPrimary: Boolean,
    val syncStatus: ChannelSyncStatus,
)
