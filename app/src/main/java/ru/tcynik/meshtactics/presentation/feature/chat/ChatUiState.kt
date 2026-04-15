package ru.tcynik.meshtactics.presentation.feature.chat

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.transport.model.ChatMessageModel
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatTab

data class ChatUiState(
    val messages: ImmutableList<ChatMessageModel> = persistentListOf(),
    val filterItems: ImmutableList<ChatFilterItem> = persistentListOf(),
    val inputText: String = "",
    val searchQuery: String = "",
    val currentTab: ChatTab = ChatTab.FILTER,
    val selectedChatId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showContextMenu: Boolean = false,
    val contextMenuItemId: String? = null,
    val chatTabTitle: String = "",
    val isChatTabEnabled: Boolean = false,
)
