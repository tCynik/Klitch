package ru.tcynik.klitch.presentation.feature.chat

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel
import ru.tcynik.klitch.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.klitch.presentation.feature.chat.model.ChatTab
import ru.tcynik.klitch.presentation.ui.UiText

data class ChatUiState(
    val allMessages: ImmutableList<ChatMessageModel> = persistentListOf(),
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
    val chatTabTitle: UiText = UiText.Static(R.string.chat_tab_title_feed),
    val isChatTabEnabled: Boolean = false,
    val isSelectedChatActive: Boolean = true,
    val selectedChatPartnerHasPKC: Boolean? = null,
    val syncRequired: Boolean = false,
)
