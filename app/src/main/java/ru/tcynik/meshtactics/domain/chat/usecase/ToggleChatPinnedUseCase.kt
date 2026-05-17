package ru.tcynik.meshtactics.domain.chat.usecase

import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

data class TogglePinnedParams(val contactId: String, val isPinned: Boolean)

class ToggleChatPinnedUseCase(
    private val repository: ChatRepository,
) : UseCase<TogglePinnedParams, Unit>() {
    override suspend fun invoke(params: TogglePinnedParams) =
        repository.togglePinned(params.contactId, params.isPinned)
}
