package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveChatContactsUseCase(
    private val repository: ChatRepository,
) : FlowUseCase<NoParams, List<ChatContact>>() {
    override fun invoke(params: NoParams): Flow<List<ChatContact>> =
        repository.observeContacts()
}
