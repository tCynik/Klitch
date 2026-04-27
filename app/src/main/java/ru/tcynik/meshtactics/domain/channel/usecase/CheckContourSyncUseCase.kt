package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourSyncResult
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class CheckContourSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
) {
    suspend operator fun invoke(): ContourSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        if (nodeChannels.isEmpty()) return ContourSyncResult.NeedsSync

        val slot0 = nodeChannels.firstOrNull { it.index == 0 }
            ?: return ContourSyncResult.NeedsSync
        if (ContourHash.compute(slot0.name, slot0.psk) != DefaultContour.CHANNEL_HASH) {
            return ContourSyncResult.NeedsSync
        }

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        for (contour in activeNonEmergency) {
            val hash = contour.transport.meshtastic.channelHash
            val matched = nodeChannels.any { slot ->
                slot.index != 0 && slot.isEnabled &&
                    ContourHash.compute(slot.name, slot.psk) == hash
            }
            if (!matched) return ContourSyncResult.NeedsSync
        }

        return ContourSyncResult.InSync
    }
}
