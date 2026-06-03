package ru.tcynik.meshtactics.data.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.mesh.repository.Notification
import ru.tcynik.meshtactics.mesh.service.AndroidNotificationManager

class EmergencyNodeNotificationFilter(
    private val androidNotificationManager: AndroidNotificationManager,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var sosMode: Boolean = false
    @Volatile private var emergencySlot: Int? = null
    private val slotToName = mutableMapOf<Int, String>()

    init {
        androidNotificationManager.nodeEventFilter = ::filter
        scope.launch {
            combine(
                contourRepository.observeSosMode(),
                contourRepository.observeContours(),
                channelSlotResolver.mapsFlow,
            ) { sos, contours, maps ->
                sosMode = sos
                emergencySlot = contours
                    .find { it.isEmergency }
                    ?.transport?.meshtastic?.channelHash
                    ?.let { maps.hashToSlot[it] }
                slotToName.clear()
                contours.forEach { c ->
                    val hash = c.transport.meshtastic.channelHash
                    val slot = maps.hashToSlot[hash] ?: return@forEach
                    slotToName[slot] = c.name
                }
            }.collect {}
        }
    }

    private fun filter(notification: Notification): Notification? {
        val slot = notification.channelSlot
        if (slot != null && slot == emergencySlot && !sosMode) return null
        val contourName = slot?.let { slotToName[it] } ?: return notification
        return notification.copy(
            title = "Новая нода ${notification.title}_${notification.message} в $contourName",
        )
    }
}
