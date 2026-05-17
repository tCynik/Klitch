package ru.tcynik.meshtactics.domain.emergency.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

private const val EMERGENCY_CHANNEL = 0

class TriggerEmergencyUseCase(
    private val contourRepository: ContourRepository,
    private val appUserRepository: AppUserRepository,
    private val gpsRepository: GpsRepository,
    private val sendChatMessage: SendChatMessageUseCase,
    private val broadcast: EmergencyPositionBroadcastRepository,
) {
    suspend operator fun invoke() {
        contourRepository.setEmergencyActive(true)

        val callsign = appUserRepository.observeUser().first().displayName.ifBlank { "Неизвестный" }
        val location = gpsRepository.location.value

        val text = if (location != null) {
            "$callsign просит помощи, координаты: ${location.latitude}, ${location.longitude}"
        } else {
            "$callsign просит помощи"
        }

        sendChatMessage(SendChatMessageParams(text = text, contactId = "^all", channel = EMERGENCY_CHANNEL))
        broadcast.start()
    }
}
