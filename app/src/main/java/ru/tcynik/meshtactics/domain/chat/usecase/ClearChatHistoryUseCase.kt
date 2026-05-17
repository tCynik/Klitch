package ru.tcynik.meshtactics.domain.chat.usecase

import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

data class ClearHistoryParams(val contactId: String)

class ClearChatHistoryUseCase(
    private val repository: ChatRepository,
) : UseCase<ClearHistoryParams, Unit>() {
    override suspend fun invoke(params: ClearHistoryParams) =
        repository.clearHistory(params.contactId)
}
