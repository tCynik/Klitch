package ru.tcynik.klitch.domain.emergency.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.klitch.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.klitch.domain.user.repository.AppUserRepository

class CancelEmergencyUseCase(
    private val contourRepository: ContourRepository,
    private val appUserRepository: AppUserRepository,
    private val sendChatMessage: SendChatMessageUseCase,
    private val broadcast: EmergencyPositionBroadcastRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    suspend operator fun invoke() {
        broadcast.stop()

        val callsign = appUserRepository.observeUser().first().displayName.ifBlank { "Неизвестный" }
        val emergencySlot = channelSlotResolver.hashToSlot[DefaultContour.CHANNEL_HASH] ?: 0
        sendChatMessage(
            SendChatMessageParams(
                text = "Пользователь $callsign отметил, что с ним всё в порядке, помощь не требуется",
                contactId = "^all",
                channel = emergencySlot,
            )
        )

        contourRepository.setSosMode(false)
    }
}
