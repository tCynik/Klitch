package ru.tcynik.klitch.domain.chat.usecase

import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

data class SendChatMessageParams(
    val text: String,
    val contactId: String,
    val channel: Int = 0,
)

class SendChatMessageUseCase(
    private val repository: ChatRepository,
) : UseCase<SendChatMessageParams, Unit>() {
    override suspend fun invoke(params: SendChatMessageParams) =
        repository.sendMessage(params.text, params.contactId, params.channel)
}
