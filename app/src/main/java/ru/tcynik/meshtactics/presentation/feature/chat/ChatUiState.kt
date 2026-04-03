package ru.tcynik.meshtactics.presentation.feature.chat

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.transport.model.ChatMessageModel

data class ChatUiState(
    val messages: ImmutableList<ChatMessageModel> = persistentListOf(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
