package ru.tcynik.klitch.domain.channel.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.SyncContoursResult
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.model.meshtasticChannelName
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.klitch.domain.mesh.usecase.IsPositionSmartBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class SyncContoursOnConnectUseCase(
    private val contourRepository: ContourRepository,
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val beginSettingsEdit: BeginSettingsEditUseCase,
    private val commitSettingsEdit: CommitSettingsEditUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val prepareNodeForAppDrivenBroadcast: PrepareNodeForAppDrivenBroadcastUseCase,
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase,
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase,
    private val observeEmergencyMode: ObserveEmergencyModeUseCase,
    private val getPositionBroadcastSecs: GetPositionBroadcastSecsUseCase,
    private val isPositionSmartBroadcastEnabled: IsPositionSmartBroadcastEnabledUseCase,
    private val logger: Logger,
) {
    suspend operator fun invoke(): SyncContoursResult {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()
        val primaryContour = contours.find { it.id == primaryId }
            ?: contours.firstOrNull { !it.isEmergency }
            ?: return SyncContoursResult.NothingToWrite

        // Resolve owner info BEFORE opening the edit session to avoid delays inside it.
        val user = observeAppUser(NoParams).first()
        val deviceConfig = if (user.displayName.isNotBlank()) {
            withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
        } else {
            null
        }
        val needsOwnerWrite = user.displayName.isNotBlank() &&
            deviceConfig?.longName != user.displayName

        // Resolve desired GPS broadcast state BEFORE opening the edit session.
        val sosActive = observeEmergencyMode().first()
        val broadcastEnabled = observeGpsBroadcastEnabled().first()
        val desiredBroadcastEnabled = !sosActive && broadcastEnabled
        val currentBroadcastSecs = getPositionBroadcastSecs()
        val currentSmartEnabled = isPositionSmartBroadcastEnabled()
        val desiredBroadcastSecs = if (desiredBroadcastEnabled) BROADCAST_READY_SECS else BROADCAST_DISABLED_SECS
        // Also write when smart broadcast is still enabled despite GPS broadcast being active —
        // smart broadcast silently extends the interval when the device is stationary, causing
        // periodic stale markers on peer devices.
        val needsBroadcastWrite = currentBroadcastSecs != null && (
            currentBroadcastSecs != desiredBroadcastSecs ||
            (desiredBroadcastEnabled && currentSmartEnabled == true)
        )

        val primaryName = meshtasticChannelName(primaryContour)
        val primaryPsk = if (primaryContour.isEmergency) {
            DefaultContour.OPEN_PSK
        } else {
            primaryContour.transport.meshtastic.psk
        }
        val expectedSlot0Hash = expectedSlot0Hash(primaryContour)
        val slot0 = nodeChannels.find { it.index == 0 }
        val primarySynced = slot0 != null &&
            slot0.name == primaryName &&
            ContourHash.compute(slot0.name, slot0.psk) == expectedSlot0Hash

        val slot1 = if (!primaryContour.isEmergency) nodeChannels.find { it.index == 1 } else null
        val emergencySynced = slot1 != null &&
            slot1.name == DefaultContour.CHANNEL_NAME &&
            ContourHash.compute(slot1.name, slot1.psk) == DefaultContour.CHANNEL_HASH &&
            slot1.positionPrecision == ChannelPositionPrecision.DISABLED

        val usedSlots = if (primaryContour.isEmergency) {
            mutableSetOf(0)
        } else {
            mutableSetOf(0, 1)
        }
        val activeNonPrimary = contours.filter { it.isActive && !it.isEmergency && it.id != primaryId }

        // Pre-resolve extra contour slots to know ahead of time if anything needs writing.
        data class ExtraWrite(val slot: Int, val contour: Contour)
        val extraWrites = mutableListOf<ExtraWrite>()
        for (contour in activeNonPrimary) {
            when (val r = resolveSlot(contour, nodeChannels, usedSlots, checkPrecision = true)) {
                is SlotResolution.AlreadySynced -> usedSlots.add(r.slot)
                is SlotResolution.FreeSlot -> {
                    extraWrites.add(ExtraWrite(r.slot, contour))
                    usedSlots.add(r.slot)
                }
                is SlotResolution.NoFreeSlot -> {
                    logger.w("Contour", "no free slots for contour '${contour.name}' — skipping")
                }
            }
        }

        val channelsNeedWrite = !primarySynced ||
            (!primaryContour.isEmergency && !emergencySynced) ||
            extraWrites.isNotEmpty()

        if (!channelsNeedWrite && !needsOwnerWrite && !needsBroadcastWrite) {
            logger.d("Contour", "nothing to write")
            return SyncContoursResult.NothingToWrite
        }

        // Open edit session — channels are buffered in firmware RAM until commit_edit_settings
        // flushes them to flash. Owner write persists independently.
        logger.i("Contour", "session_passkey: opening edit session for channel sync")
        if (!beginSettingsEdit.invoke()) {
            logger.w("Contour", "session_passkey: beginSettingsEdit failed — no edit session")
            return SyncContoursResult.FailedNoSession
        }

        if (!primarySynced) {
            logger.d("Contour", "write primary slot 0 name='$primaryName'")
            writeChannel(0, primaryName, primaryPsk)
        }

        if (!primaryContour.isEmergency && !emergencySynced) {
            logger.d("Contour", "write emergency slot 1")
            writeChannel(
                1,
                DefaultContour.CHANNEL_NAME,
                DefaultContour.OPEN_PSK,
                ChannelPositionPrecision.DISABLED,
            )
        }

        for ((slot, contour) in extraWrites) {
            writeChannel(slot, meshtasticChannelName(contour), contour.transport.meshtastic.psk)
        }

        if (needsOwnerWrite) {
            logger.d("Contour", "writeOwner longName='${user.displayName}'")
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }

        if (needsBroadcastWrite) {
            logger.d("Contour", "write position_broadcast_secs=$desiredBroadcastSecs (broadcastEnabled=$desiredBroadcastEnabled)")
            if (desiredBroadcastEnabled) prepareNodeForAppDrivenBroadcast() else disableNodePositionBroadcast()
        }

        // Commit flushes all buffered channel changes to flash.
        // Firmware may reboot the node as part of commit; the caller's rebootNode() is a fallback.
        commitSettingsEdit()
        return SyncContoursResult.Success
    }

    private fun expectedSlot0Hash(primaryContour: Contour): ContourHash =
        if (primaryContour.isEmergency) DefaultContour.CHANNEL_HASH
        else primaryContour.transport.meshtastic.channelHash

    private companion object {
        // Both READY and DISABLED write Int.MAX_VALUE — firmware never broadcasts autonomously.
        // The distinction is behavioral: prepareNodeForAppDrivenBroadcast() also disables
        // is_power_saving so the app can send positions while the screen is off.
        const val BROADCAST_READY_SECS = Int.MAX_VALUE
        const val BROADCAST_DISABLED_SECS = Int.MAX_VALUE
    }
}
