package ru.tcynik.klitch.domain.chat.usecase

import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

data class ToggleFavoriteParams(val contactId: String, val isFavorite: Boolean)

class ToggleChatFavoriteUseCase(
    private val repository: ChatRepository,
) : UseCase<ToggleFavoriteParams, Unit>() {
    override suspend fun invoke(params: ToggleFavoriteParams) =
        repository.toggleFavorite(params.contactId, params.isFavorite)
}
