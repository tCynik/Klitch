package ru.tcynik.meshtactics.domain.chat.usecase

import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

data class ToggleArchivedParams(val contactId: String, val isArchived: Boolean)

class ToggleChatArchivedUseCase(
    private val repository: ChatRepository,
) : UseCase<ToggleArchivedParams, Unit>() {
    override suspend fun invoke(params: ToggleArchivedParams) =
        repository.toggleArchived(params.contactId, params.isArchived)
}
