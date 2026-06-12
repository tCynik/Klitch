package ru.tcynik.klitch.domain.chat.usecase

import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

data class ClearHistoryParams(val contactId: String)

class ClearChatHistoryUseCase(
    private val repository: ChatRepository,
) : UseCase<ClearHistoryParams, Unit>() {
    override suspend fun invoke(params: ClearHistoryParams) =
        repository.clearHistory(params.contactId)
}
