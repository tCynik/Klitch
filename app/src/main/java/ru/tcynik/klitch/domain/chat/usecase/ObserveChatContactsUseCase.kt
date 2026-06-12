package ru.tcynik.klitch.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.chat.model.ChatContact
import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveChatContactsUseCase(
    private val repository: ChatRepository,
) : FlowUseCase<NoParams, List<ChatContact>>() {
    override fun invoke(params: NoParams): Flow<List<ChatContact>> =
        repository.observeContacts()
}
