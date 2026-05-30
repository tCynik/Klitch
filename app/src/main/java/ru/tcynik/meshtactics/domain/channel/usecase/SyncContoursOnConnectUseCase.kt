package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
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

        val primaryName = meshtasticChannelName(primaryContour)
        val primaryPsk = if (primaryContour.isEmergency) {
            DefaultContour.OPEN_PSK
        } else {
            primaryContour.transport.meshtastic.psk
        }
        val expectedSlot0Hash = expectedSlot0Hash(primaryContour)
        val slot0 = nodeChannels.find { it.index == 0 }
        val primarySynced = slot0 != null &&
            ContourHash.compute(slot0.name, slot0.psk) == expectedSlot0Hash

        var channelEditOpen = false
        var wroteChannels = false

        suspend fun ensureChannelEditOpen() {
            if (!channelEditOpen) {
                beginSettingsEdit()
                channelEditOpen = true
            }
        }

        if (!primarySynced) {
            logger.d("Contour", "write primary slot 0 name='$primaryName'")
            ensureChannelEditOpen()
            writeChannel(0, primaryName, primaryPsk)
            wroteChannels = true
        }

        if (!primaryContour.isEmergency) {
            val slot1 = nodeChannels.find { it.index == 1 }
            val emergencySynced = slot1 != null &&
                ContourHash.compute(slot1.name, slot1.psk) == DefaultContour.CHANNEL_HASH
            if (!emergencySynced) {
                logger.d("Contour", "write emergency slot 1")
                ensureChannelEditOpen()
                writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
                wroteChannels = true
            }
        }

        val usedSlots = if (primaryContour.isEmergency) {
            mutableSetOf(0)
        } else {
            mutableSetOf(0, 1)
        }

        val activeNonPrimary = contours.filter { it.isActive && !it.isEmergency && it.id != primaryId }
        for (contour in activeNonPrimary) {
            when (val resolution = resolveSlot(contour, nodeChannels, usedSlots, checkPrecision = true)) {
                is SlotResolution.AlreadySynced -> usedSlots.add(resolution.slot)
                is SlotResolution.FreeSlot -> {
                    ensureChannelEditOpen()
                    writeChannel(resolution.slot, meshtasticChannelName(contour), contour.transport.meshtastic.psk)
                    usedSlots.add(resolution.slot)
                    wroteChannels = true
                }
                is SlotResolution.NoFreeSlot -> {
                    logger.w("Contour", "no free slots for contour '${contour.name}' — skipping")
                    if (channelEditOpen) commitSettingsEdit()
                    return
                }
            }
        }

        if (wroteChannels) {
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
            if (needsOwnerWrite) {
                logger.d("Contour", "writeOwner longName='${user.displayName}'")
                writeOwner(user.displayName, deviceConfig?.shortName ?: "")
            }
            commitSettingsEdit()
            delay(COMMIT_SETTLE_MS)
        } else {
            val user = observeAppUser(NoParams).first()
            if (user.displayName.isNotBlank()) {
                val deviceConfig = withTimeoutOrNull(5_000) {
                    observeDeviceConfig(NoParams).first { it != null }
                }
                if (deviceConfig?.longName != user.displayName) {
                    logger.d("Contour", "writeOwner longName='${user.displayName}'")
                    writeOwner(user.displayName, deviceConfig?.shortName ?: "")
                }
            }
        }
    }

    private fun meshtasticChannelName(contour: Contour): String =
        when (contour.id) {
            DefaultActiveContour.ID -> DefaultActiveContour.CHANNEL_NAME
            DefaultContour.ID -> DefaultContour.CHANNEL_NAME
            else -> contour.name
        }

    private fun expectedSlot0Hash(primaryContour: Contour): ContourHash =
        if (primaryContour.isEmergency) DefaultContour.CHANNEL_HASH
        else primaryContour.transport.meshtastic.channelHash

    private companion object {
        /** Дать ноде время записать commit_edit_settings на flash до reboot. */
        const val COMMIT_SETTLE_MS = 3_000L
    }
}
