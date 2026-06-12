package ru.tcynik.klitch.domain.chat.usecase

import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

data class TogglePinnedParams(val contactId: String, val isPinned: Boolean)

class ToggleChatPinnedUseCase(
    private val repository: ChatRepository,
) : UseCase<TogglePinnedParams, Unit>() {
    override suspend fun invoke(params: TogglePinnedParams) =
        repository.togglePinned(params.contactId, params.isPinned)
}
