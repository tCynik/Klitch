package ru.tcynik.meshtactics.domain.mesh.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "NodeProvisioning"

class NodeProvisioningUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
) {
    suspend fun provision() {
        Log.d(TAG, "provision() started")
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()

        val usedSlots = mutableSetOf<Int>()
        contours.forEach { contour ->
            if (contour.isEmergency) return@forEach
            when (val r = resolveSlot(contour, nodeChannels, usedSlots)) {
                is SlotResolution.AlreadySynced -> {
                    Log.d(TAG, "  skip '${contour.name}' — already synced at slot ${r.slot}")
                    usedSlots.add(r.slot)
                }
                is SlotResolution.FreeSlot -> {
                    Log.d(TAG, "  writeChannel slot=${r.slot} name='${contour.name}'")
                    writeChannel(r.slot, contour.name, contour.transport.meshtastic.psk)
                    usedSlots.add(r.slot)
                }
                is SlotResolution.NoFreeSlot -> Log.w(TAG, "  no free slots for '${contour.name}' — skipping")
            }
        }
        Log.d(TAG, "provision() done")
    }
}
