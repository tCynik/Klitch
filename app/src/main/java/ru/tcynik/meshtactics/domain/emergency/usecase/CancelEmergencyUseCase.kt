package ru.tcynik.meshtactics.domain.emergency.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

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
