package ru.tcynik.meshtactics.domain.channel.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourSyncResult
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "CheckContourSync"

class CheckContourSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
) {
    suspend operator fun invoke(): ContourSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        Log.d(TAG, "check: localContours=${contours.size} nodeChannels=${nodeChannels.size}")

        if (nodeChannels.isEmpty()) {
            Log.d(TAG, "InSync: channel data not yet available — skipping check")
            return ContourSyncResult.InSync
        }

        val slot0 = nodeChannels.firstOrNull { it.index == 0 }
        if (slot0 == null) {
            Log.w(TAG, "NeedsSync: slot 0 missing on node")
            return ContourSyncResult.NeedsSync
        }
        val slot0Hash = ContourHash.compute(slot0.name, slot0.psk)
        if (slot0Hash != DefaultContour.CHANNEL_HASH) {
            val pskHex = slot0.psk.joinToString("") { "%02x".format(it) }
            Log.w(TAG, "NeedsSync: slot0 hash mismatch — got=$slot0Hash expected=${DefaultContour.CHANNEL_HASH} name='${slot0.name}' psk=$pskHex")
            return ContourSyncResult.NeedsSync
        }

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        Log.d(TAG, "activeNonEmergency contours to check: ${activeNonEmergency.map { it.name }}")

        for (contour in activeNonEmergency) {
            val hash = contour.transport.meshtastic.channelHash
            val matched = nodeChannels.any { slot ->
                slot.index != 0 && slot.isEnabled && slot.positionPrecision > 0 &&
                    ContourHash.compute(slot.name, slot.psk) == hash
            }
            if (!matched) {
                Log.w(TAG, "NeedsSync: contour '${contour.name}' hash=$hash psk='${contour.transport.meshtastic.psk}' not found on node")
                Log.w(TAG, "  nodeChannels(${nodeChannels.size}):")
                nodeChannels.forEach { slot ->
                    val slotHash = ContourHash.compute(slot.name, slot.psk)
                    val pskHex = slot.psk.joinToString("") { "%02x".format(it) }
                    Log.w(TAG, "    [${slot.index}] name='${slot.name}' enabled=${slot.isEnabled} hash=$slotHash psk=$pskHex")
                }
                return ContourSyncResult.NeedsSync
            }
        }

        val user = observeAppUser(NoParams).first()
        if (user.displayName.isNotBlank()) {
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            if (deviceConfig != null && deviceConfig.longName != user.displayName) {
                Log.w(TAG, "NeedsSync: owner name mismatch — node='${deviceConfig.longName}' app='${user.displayName}'")
                return ContourSyncResult.NeedsSync
            }
        }

        Log.d(TAG, "InSync: all checks passed")
        return ContourSyncResult.InSync
    }
}
