package ru.tcynik.meshtactics.presentation.feature.settings.models

import ru.tcynik.meshtactics.domain.channel.model.ChannelSyncStatus
import ru.tcynik.meshtactics.domain.channel.model.ContourId
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
