package ru.tcynik.meshtactics.domain.channel.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "SyncContoursOnConnect"

class SyncContoursOnConnectUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
) {
    suspend operator fun invoke() {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        val slot0 = nodeChannels.find { it.index == 0 }
        val emergencyAlreadySynced = slot0 != null &&
            ContourHash.compute(slot0.name, slot0.psk) == DefaultContour.CHANNEL_HASH
        if (!emergencyAlreadySynced) {
            writeChannel(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
        }

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        val usedSlots = mutableSetOf<Int>()
        for (contour in activeNonEmergency) {
            when (val resolution = resolveSlot(contour, nodeChannels, usedSlots, checkPrecision = true)) {
                is SlotResolution.AlreadySynced -> usedSlots.add(resolution.slot)
                is SlotResolution.FreeSlot -> {
                    writeChannel(resolution.slot, contour.name, contour.transport.meshtastic.psk)
                    usedSlots.add(resolution.slot)
                }
                is SlotResolution.NoFreeSlot -> {
                    Log.w(TAG, "no free slots for contour '${contour.name}' — skipping")
                    // TODO(contour): обработать отсутствие свободных слотов (UI уведомление)
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
                Log.d(TAG, "writeOwner longName='${user.displayName}'")
                writeOwner(user.displayName, deviceConfig?.shortName ?: "")
            }
        }
    }
}
