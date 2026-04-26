package ru.tcynik.meshtactics.domain.channel.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "SyncContoursOnConnect"

class SyncContoursOnConnectUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
) {
    suspend operator fun invoke() {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        // Slot 0 always reserved for Emergency (standard primary channel, open PSK)
        writeChannel(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)

        val activeNonEmergency = contours.filter { it.isActive && !it.isEmergency }
        for (contour in activeNonEmergency) {
            when (val resolution = resolveSlot(contour, nodeChannels)) {
                is SlotResolution.AlreadySynced -> Unit
                is SlotResolution.FreeSlot -> writeChannel(resolution.slot, contour.name, contour.transport.meshtastic.psk)
                is SlotResolution.NoFreeSlot -> {
                    Log.w(TAG, "no free slots for contour '${contour.name}' — skipping")
                    // TODO(contour): обработать отсутствие свободных слотов (UI уведомление)
                    return
                }
            }
        }
    }
}
