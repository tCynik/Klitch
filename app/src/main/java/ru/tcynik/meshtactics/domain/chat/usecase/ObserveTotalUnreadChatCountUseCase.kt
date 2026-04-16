package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveTotalUnreadChatCountUseCase(
    private val repository: ChatRepository,
) : FlowUseCase<NoParams, Int>() {
    override fun invoke(params: NoParams): Flow<Int> =
        repository.observeTotalUnreadCount()
}
