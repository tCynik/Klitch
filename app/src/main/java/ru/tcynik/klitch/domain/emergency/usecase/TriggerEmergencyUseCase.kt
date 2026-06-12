package ru.tcynik.klitch.domain.emergency.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.klitch.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.user.repository.AppUserRepository

class TriggerEmergencyUseCase(
    private val contourRepository: ContourRepository,
    private val appUserRepository: AppUserRepository,
    private val gpsRepository: GpsRepository,
    private val sendChatMessage: SendChatMessageUseCase,
    private val broadcast: EmergencyPositionBroadcastRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    suspend operator fun invoke() {
        contourRepository.setSosMode(true)

        val callsign = appUserRepository.observeUser().first().displayName.ifBlank { "Неизвестный" }
        val location = gpsRepository.location.value

        val text = if (location != null) {
            "$callsign просит помощи, координаты: ${location.latitude}, ${location.longitude}"
        } else {
            "$callsign просит помощи"
        }

        val emergencySlot = channelSlotResolver.hashToSlot[DefaultContour.CHANNEL_HASH] ?: 0
        sendChatMessage(SendChatMessageParams(text = text, contactId = "^all", channel = emergencySlot))
        broadcast.start()
    }
}
