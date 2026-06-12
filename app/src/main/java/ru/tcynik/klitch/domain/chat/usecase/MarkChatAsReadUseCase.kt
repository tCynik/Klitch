package ru.tcynik.klitch.domain.chat.usecase

import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

data class MarkAsReadParams(val contactId: String)

class MarkChatAsReadUseCase(
    private val repository: ChatRepository,
) : UseCase<MarkAsReadParams, Unit>() {
    override suspend fun invoke(params: MarkAsReadParams) =
        repository.markAsRead(params.contactId)
}
