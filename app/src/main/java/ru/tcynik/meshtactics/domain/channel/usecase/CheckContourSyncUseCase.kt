package ru.tcynik.meshtactics.domain.channel.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourSyncResult
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "CheckContourSync"

class CheckContourSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
) {
    suspend operator fun invoke(): ContourSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        Log.d(TAG, "check: localContours=${contours.size} nodeChannels=${nodeChannels.size}")

        if (nodeChannels.isEmpty()) {
            Log.w(TAG, "NeedsSync: node has no channels")
            return ContourSyncResult.NeedsSync
        }

        val slot0 = nodeChannels.firstOrNull { it.index == 0 }
        if (slot0 == null) {
            Log.w(TAG, "NeedsSync: slot 0 missing on node")
            return ContourSyncResult.NeedsSync
        }
        val slot0Hash = ContourHash.compute(slot0.name, slot0.psk)
        if (slot0Hash != DefaultContour.CHANNEL_HASH) {
            Log.w(TAG, "NeedsSync: slot0 hash mismatch — got=$slot0Hash expected=${DefaultContour.CHANNEL_HASH} (name='${slot0.name}')")
            return ContourSyncResult.NeedsSync
        }

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        Log.d(TAG, "activeNonEmergency contours to check: ${activeNonEmergency.map { it.name }}")

        for (contour in activeNonEmergency) {
            val hash = contour.transport.meshtastic.channelHash
            val matched = nodeChannels.any { slot ->
                slot.index != 0 && slot.isEnabled &&
                    ContourHash.compute(slot.name, slot.psk) == hash
            }
            if (!matched) {
                Log.w(TAG, "NeedsSync: contour '${contour.name}' (hash=$hash) not found on node")
                Log.d(TAG, "  nodeChannels: ${nodeChannels.map { "[${it.index}] '${it.name}' enabled=${it.isEnabled} hash=${ContourHash.compute(it.name, it.psk)}" }}")
                return ContourSyncResult.NeedsSync
            }
        }

        Log.d(TAG, "InSync: all checks passed")
        return ContourSyncResult.InSync
    }
}
