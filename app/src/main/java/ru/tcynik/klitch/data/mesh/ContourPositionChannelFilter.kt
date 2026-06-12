package ru.tcynik.klitch.data.mesh

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.mesh.repository.PositionChannelFilter

// Filtering is by CHANNEL (contour identity = PSK), not by INDEX (slot position).
// MeshPacket.channel = local slot index whose PSK decrypted the packet → slotToHash[N] → contour hash.
// This correctly identifies the contour regardless of slot ordering differences between devices.
class ContourPositionChannelFilter(
    contourRepository: ContourRepository,
    channelSlotResolver: ChannelSlotResolver,
) : PositionChannelFilter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSlots = MutableStateFlow<Set<Int>>(emptySet())

    init {
        scope.launch {
            combine(
                contourRepository.observeContours(),
                contourRepository.observeSosMode(),
                channelSlotResolver.mapsFlow,
            ) { contours, sosMode, maps ->
                val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }
                buildSet {
                    maps.slotToHash.forEach { (slot, hash) ->
                        val contour = contourByHash[hash] ?: return@forEach
                        val active = if (contour.isEmergency) sosMode else contour.isActive
                        if (active) add(slot)
                    }
                }
            }.collect { slots ->
                Log.d("MT/PosFilter", "activeSlots updated: $slots")
                activeSlots.value = slots
            }
        }
    }

    override fun isChannelAccepted(channel: Int): Boolean {
        val accepted = channel in activeSlots.value
        if (!accepted) Log.d("MT/PosFilter", "channel=$channel rejected, activeSlots=${activeSlots.value}")
        return accepted
    }
}
