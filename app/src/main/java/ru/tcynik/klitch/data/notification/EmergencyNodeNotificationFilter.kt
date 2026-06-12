package ru.tcynik.klitch.data.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.mesh.repository.Notification
import ru.tcynik.klitch.mesh.service.AndroidNotificationManager

class EmergencyNodeNotificationFilter(
    private val androidNotificationManager: AndroidNotificationManager,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var sosMode: Boolean = false
    @Volatile private var emergencySlot: Int? = null
    @Volatile private var emergencyContourName: String? = null

    init {
        androidNotificationManager.nodeEventFilter = ::filter
        scope.launch {
            combine(
                contourRepository.observeSosMode(),
                contourRepository.observeContours(),
                channelSlotResolver.mapsFlow,
            ) { sos, contours, maps ->
                sosMode = sos
                val emergency = contours.find { it.isEmergency }
                emergencySlot = emergency
                    ?.transport?.meshtastic?.channelHash
                    ?.let { maps.hashToSlot[it] }
                emergencyContourName = emergency?.name
            }.collect {}
        }
    }

    private fun filter(notification: Notification): Notification? {
        val slot = notification.channelSlot
        val isEmergency = slot != null && slot == emergencySlot
        if (isEmergency && !sosMode) return null
        if (isEmergency) {
            val name = emergencyContourName ?: return notification
            return notification.copy(
                title = "Новая нода ${notification.title}_${notification.message} в $name",
            )
        }
        return notification
    }
}
