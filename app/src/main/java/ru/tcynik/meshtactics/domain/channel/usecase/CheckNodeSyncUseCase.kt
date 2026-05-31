package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.logger.Logger
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class CheckNodeSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val contourRepository: ContourRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(): NodeSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()
        val primaryContour = contours.find { it.id == primaryId }
            ?: contours.firstOrNull { !it.isEmergency }

        logger.d("Contour", "check: localContours=${contours.size} nodeChannels=${nodeChannels.size} primary=${primaryContour?.name}")

        if (nodeChannels.isEmpty()) {
            logger.d("Contour", "InSync: channel data not yet available — skipping check")
            return NodeSyncResult.InSync
        }

        val slot0 = nodeChannels.firstOrNull { it.index == 0 }
        if (slot0 == null) {
            logger.w("Contour", "NeedsSync: slot 0 missing on node")
            return NodeSyncResult.NeedsSync
        }

        if (primaryContour != null) {
            val expectedSlot0Hash = if (primaryContour.isEmergency) {
                DefaultContour.CHANNEL_HASH
            } else {
                primaryContour.transport.meshtastic.channelHash
            }
            val expectedSlot0Name = meshtasticChannelName(primaryContour)
            val slot0Hash = ContourHash.compute(slot0.name, slot0.psk)
            if (slot0.name != expectedSlot0Name || slot0Hash != expectedSlot0Hash) {
                val pskHex = slot0.psk.joinToString("") { "%02x".format(it) }
                logger.w("Contour", "NeedsSync: slot0 mismatch — got name='${slot0.name}' hash=$slot0Hash expected name='$expectedSlot0Name' hash=$expectedSlot0Hash psk=$pskHex")
                return NodeSyncResult.NeedsSync
            }

            if (!primaryContour.isEmergency) {
                val slot1 = nodeChannels.firstOrNull { it.index == 1 }
                val slot1Hash = slot1?.let { ContourHash.compute(it.name, it.psk) }
                if (slot1?.name != DefaultContour.CHANNEL_NAME || slot1Hash != DefaultContour.CHANNEL_HASH) {
                    logger.w("Contour", "NeedsSync: slot1 emergency mismatch — got name='${slot1?.name}' hash=$slot1Hash expected name='${DefaultContour.CHANNEL_NAME}' hash=${DefaultContour.CHANNEL_HASH}")
                    return NodeSyncResult.NeedsSync
                }
            }
        }

        val activeNonPrimaryNonEmergency = contours.filter { it.isActive && !it.isEmergency && it.id != primaryId }
        logger.d("Contour", "active non-primary non-emergency to check: ${activeNonPrimaryNonEmergency.map { it.name }}")

        for (contour in activeNonPrimaryNonEmergency) {
            val hash = contour.transport.meshtastic.channelHash
            val expectedName = meshtasticChannelName(contour)
            val matched = nodeChannels.any { slot ->
                slot.index > 1 && slot.isEnabled && slot.positionPrecision > 0 &&
                    slot.name == expectedName &&
                    ContourHash.compute(slot.name, slot.psk) == hash
            }
            if (!matched) {
                logger.w("Contour", "NeedsSync: contour '${contour.name}' hash=$hash psk='${contour.transport.meshtastic.psk}' not found on node")
                logger.w("Contour", "  nodeChannels(${nodeChannels.size}):")
                nodeChannels.forEach { slot ->
                    val slotHash = ContourHash.compute(slot.name, slot.psk)
                    val pskHex = slot.psk.joinToString("") { "%02x".format(it) }
                    logger.w("Contour", "    [${slot.index}] name='${slot.name}' enabled=${slot.isEnabled} hash=$slotHash psk=$pskHex")
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
                logger.w("Contour", "NeedsSync: owner name mismatch — node='${deviceConfig.longName}' app='${user.displayName}'")
                return NodeSyncResult.NeedsSync
            }
        }

        logger.d("Contour", "InSync: all checks passed")
        return NodeSyncResult.InSync
    }
}
