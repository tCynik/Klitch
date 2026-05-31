package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

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
    private val logger: Logger,
) {
    suspend operator fun invoke() {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()
        val primaryContour = contours.find { it.id == primaryId }
            ?: contours.firstOrNull { !it.isEmergency }
            ?: return

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
            ContourHash.compute(slot1.name, slot1.psk) == DefaultContour.CHANNEL_HASH

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

        if (!channelsNeedWrite && !needsOwnerWrite) {
            logger.d("Contour", "nothing to write")
            return
        }

        // Open edit session — channels are buffered in firmware RAM until commit_edit_settings
        // flushes them to flash. Owner write persists independently.
        beginSettingsEdit()

        if (!primarySynced) {
            logger.d("Contour", "write primary slot 0 name='$primaryName'")
            writeChannel(0, primaryName, primaryPsk)
        }

        if (!primaryContour.isEmergency && !emergencySynced) {
            logger.d("Contour", "write emergency slot 1")
            writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
        }

        for ((slot, contour) in extraWrites) {
            writeChannel(slot, meshtasticChannelName(contour), contour.transport.meshtastic.psk)
        }

        if (needsOwnerWrite) {
            logger.d("Contour", "writeOwner longName='${user.displayName}'")
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }

        // Commit flushes all buffered channel changes to flash.
        // Firmware may reboot the node as part of commit; the caller's rebootNode() is a fallback.
        commitSettingsEdit()
    }

    private fun expectedSlot0Hash(primaryContour: Contour): ContourHash =
        if (primaryContour.isEmergency) DefaultContour.CHANNEL_HASH
        else primaryContour.transport.meshtastic.channelHash
}
