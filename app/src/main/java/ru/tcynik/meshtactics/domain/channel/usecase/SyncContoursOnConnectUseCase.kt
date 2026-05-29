package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class SyncContoursOnConnectUseCase(
    private val contourRepository: ContourRepository,
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
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

        val primaryName = if (primaryContour.isEmergency) {
            DefaultContour.CHANNEL_NAME
        } else {
            primaryContour.name
        }
        val primaryPsk = if (primaryContour.isEmergency) {
            DefaultContour.OPEN_PSK
        } else {
            primaryContour.transport.meshtastic.psk
        }
        val slot0 = nodeChannels.find { it.index == 0 }
        val primarySynced = slot0 != null &&
            ContourHash.compute(slot0.name, slot0.psk) == primaryContour.transport.meshtastic.channelHash
        if (!primarySynced) {
            writeChannel(0, primaryName, primaryPsk)
        }

        if (!primaryContour.isEmergency) {
            val slot1 = nodeChannels.find { it.index == 1 }
            val emergencySynced = slot1 != null &&
                ContourHash.compute(slot1.name, slot1.psk) == DefaultContour.CHANNEL_HASH
            if (!emergencySynced) {
                writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
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
                    writeChannel(resolution.slot, contour.name, contour.transport.meshtastic.psk)
                    usedSlots.add(resolution.slot)
                }
                is SlotResolution.NoFreeSlot -> {
                    logger.w("Contour", "no free slots for contour '${contour.name}' — skipping")
                    return
                }
            }
        }

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
