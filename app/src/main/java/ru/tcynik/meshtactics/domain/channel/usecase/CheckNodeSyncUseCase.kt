package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.logger.Logger
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class CheckNodeSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val logger: Logger,
) {
    suspend operator fun invoke(): NodeSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        logger.d("Contour","check: localContours=${contours.size} nodeChannels=${nodeChannels.size}")

        if (nodeChannels.isEmpty()) {
            logger.d("Contour","InSync: channel data not yet available — skipping check")
            return NodeSyncResult.InSync
        }

        val slot0 = nodeChannels.firstOrNull { it.index == 0 }
        if (slot0 == null) {
            logger.w("Contour","NeedsSync: slot 0 missing on node")
            return NodeSyncResult.NeedsSync
        }
        val slot0Hash = ContourHash.compute(slot0.name, slot0.psk)
        if (slot0Hash != DefaultContour.CHANNEL_HASH) {
            val pskHex = slot0.psk.joinToString("") { "%02x".format(it) }
            logger.w("Contour","NeedsSync: slot0 hash mismatch — got=$slot0Hash expected=${DefaultContour.CHANNEL_HASH} name='${slot0.name}' psk=$pskHex")
            return NodeSyncResult.NeedsSync
        }

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        logger.d("Contour","activeNonEmergency contours to check: ${activeNonEmergency.map { it.name }}")

        for (contour in activeNonEmergency) {
            val hash = contour.transport.meshtastic.channelHash
            val matched = nodeChannels.any { slot ->
                slot.index != 0 && slot.isEnabled && slot.positionPrecision > 0 &&
                    ContourHash.compute(slot.name, slot.psk) == hash
            }
            if (!matched) {
                logger.w("Contour","NeedsSync: contour '${contour.name}' hash=$hash psk='${contour.transport.meshtastic.psk}' not found on node")
                logger.w("Contour","  nodeChannels(${nodeChannels.size}):")
                nodeChannels.forEach { slot ->
                    val slotHash = ContourHash.compute(slot.name, slot.psk)
                    val pskHex = slot.psk.joinToString("") { "%02x".format(it) }
                    logger.w("Contour","    [${slot.index}] name='${slot.name}' enabled=${slot.isEnabled} hash=$slotHash psk=$pskHex")
                }
                return NodeSyncResult.NeedsSync
            }
        }

        val user = observeAppUser(NoParams).first()
        if (user.displayName.isNotBlank()) {
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            if (deviceConfig != null && deviceConfig.longName != user.displayName) {
                logger.w("Contour","NeedsSync: owner name mismatch — node='${deviceConfig.longName}' app='${user.displayName}'")
                return NodeSyncResult.NeedsSync
            }
        }

        logger.d("Contour","InSync: all checks passed")
        return NodeSyncResult.InSync
    }
}
