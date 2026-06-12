package ru.tcynik.klitch.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel
import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase

data class ObserveChatMessagesParams(
    val contactIds: Set<String>,
    val searchQuery: String = "",
)

class ObserveChatMessagesUseCase(
    private val repository: ChatRepository,
) : FlowUseCase<ObserveChatMessagesParams, List<ChatMessageModel>>() {
    override fun invoke(params: ObserveChatMessagesParams): Flow<List<ChatMessageModel>> =
        repository.observeMessages(params.contactIds, params.searchQuery)
}
