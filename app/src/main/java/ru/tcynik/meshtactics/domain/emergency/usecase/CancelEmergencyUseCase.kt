package ru.tcynik.meshtactics.domain.emergency.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

private const val EMERGENCY_CHANNEL = 0

class CancelEmergencyUseCase(
    private val contourRepository: ContourRepository,
    private val setPrimaryContour: SetPrimaryContourUseCase,
    private val appUserRepository: AppUserRepository,
    private val sendChatMessage: SendChatMessageUseCase,
    private val broadcast: EmergencyPositionBroadcastRepository,
) {
    suspend operator fun invoke() {
        broadcast.stop()

        val callsign = appUserRepository.observeUser().first().displayName.ifBlank { "Неизвестный" }

        sendChatMessage(
            SendChatMessageParams(
                text = "Пользователь $callsign отметил, что с ним всё в порядке, помощь не требуется",
                contactId = "^all",
                channel = EMERGENCY_CHANNEL,
            )
        )

        val preSosId = contourRepository.getPreSosPrimaryId() ?: DefaultActiveContour.ID
        setPrimaryContour(preSosId)
        contourRepository.setSosMode(false)
        contourRepository.savePreSosPrimaryId(null)
    }
}
