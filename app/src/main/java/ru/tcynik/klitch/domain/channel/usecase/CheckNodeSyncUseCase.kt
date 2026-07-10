package ru.tcynik.klitch.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.klitch.domain.logger.Logger
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.NodeSyncResult
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.model.meshtasticChannelName
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.usecase.GetDesiredGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.klitch.domain.mesh.usecase.IsPositionSmartBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class CheckNodeSyncUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val contourRepository: ContourRepository,
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase,
    private val observeEmergencyMode: ObserveEmergencyModeUseCase,
    private val getPositionBroadcastSecs: GetPositionBroadcastSecsUseCase,
    private val isPositionSmartBroadcastEnabled: IsPositionSmartBroadcastEnabledUseCase,
    private val getDesiredGpsMode: GetDesiredGpsModeUseCase,
    private val getGpsMode: GetGpsModeUseCase,
    private val logger: Logger,
) {
    suspend operator fun invoke(): NodeSyncResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()
        val primaryContour = contours.find { it.id == primaryId }
            ?: contours.firstOrNull { !it.isEmergency }

        val sosActive = observeEmergencyMode().first()
        logger.d("Contour", "check: localContours=${contours.size} nodeChannels=${nodeChannels.size} primary=${primaryContour?.name} sosActive=$sosActive")

        if (nodeChannels.isEmpty()) {
            logger.d("Contour", "InSync: channel data not yet available — skipping check")
            return NodeSyncResult.InSync
        }

        if (!sosActive) {
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
                    if (slot1?.name != DefaultContour.CHANNEL_NAME ||
                        slot1Hash != DefaultContour.CHANNEL_HASH ||
                        slot1.positionPrecision != ChannelPositionPrecision.DISABLED
                    ) {
                        logger.w(
                            "Contour",
                            "NeedsSync: slot1 emergency mismatch — got name='${slot1?.name}' hash=$slot1Hash precision=${slot1?.positionPrecision} " +
                                "expected name='${DefaultContour.CHANNEL_NAME}' hash=${DefaultContour.CHANNEL_HASH} precision=${ChannelPositionPrecision.DISABLED}",
                        )
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

        val broadcastEnabled = observeGpsBroadcastEnabled().first()
        val desiredBroadcastEnabled = !sosActive && broadcastEnabled
        val currentSecs = getPositionBroadcastSecs()
        if (currentSecs != null) {
            val desiredGpsMode = getDesiredGpsMode()
            val currentGpsMode = getGpsMode()
            if (desiredGpsMode != null && currentGpsMode != null && currentGpsMode != desiredGpsMode) {
                logger.w("Contour", "NeedsSync: gps_mode mismatch — current=$currentGpsMode desired=$desiredGpsMode")
                return NodeSyncResult.NeedsSync
            }

            // NODE_GPS (node's own GPS chip is the source, gps_mode=ENABLED): position_broadcast_secs
            // and smart-broadcast are BackgroundPositionSession.ensureNodeGpsPreset()'s preset (180s,
            // smart on), not the PHONE_GPS app-driven one below — checking against the PHONE_GPS
            // preset here caused a permanent mismatch that fought BackgroundPositionSession's writes
            // (each side rebooting the node to re-assert its own value) on every reconnect.
            val effectiveGpsMode = desiredGpsMode ?: currentGpsMode
            if (effectiveGpsMode != GpsMode.ENABLED) {
                val desiredSecs = if (desiredBroadcastEnabled) BROADCAST_READY_SECS else BROADCAST_DISABLED_SECS
                if (currentSecs != desiredSecs) {
                    logger.w("Contour", "NeedsSync: position_broadcast_secs mismatch — current=$currentSecs desired=$desiredSecs")
                    return NodeSyncResult.NeedsSync
                }
                if (desiredBroadcastEnabled && isPositionSmartBroadcastEnabled() == true) {
                    logger.w("Contour", "NeedsSync: smart_broadcast still enabled despite app-driven mode")
                    return NodeSyncResult.NeedsSync
                }
            }
        }

        logger.d("Contour", "InSync: all checks passed")
        return NodeSyncResult.InSync
    }

    private companion object {
        // Must stay in sync with SyncContoursOnConnectUseCase.BROADCAST_READY_SECS
        const val BROADCAST_READY_SECS = Int.MAX_VALUE
        const val BROADCAST_DISABLED_SECS = Int.MAX_VALUE
    }
}
