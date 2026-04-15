package ru.tcynik.meshtactics.presentation.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.data.chat.prefs.ChatPrefsRepository
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ContactType
import ru.tcynik.meshtactics.domain.chat.usecase.ClearChatHistoryUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ClearHistoryParams
import ru.tcynik.meshtactics.domain.chat.usecase.MarkAsReadParams
import ru.tcynik.meshtactics.domain.chat.usecase.MarkChatAsReadUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveChatContactsUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveChatMessagesParams
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ToggleArchivedParams
import ru.tcynik.meshtactics.domain.chat.usecase.ToggleChatArchivedUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ToggleChatFavoriteUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ToggleChatPinnedUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ToggleFavoriteParams
import ru.tcynik.meshtactics.domain.chat.usecase.TogglePinnedParams
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatTab
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatType

class ChatViewModel(
    private val observeContactsUseCase: ObserveChatContactsUseCase,
    private val observeMessagesUseCase: ObserveChatMessagesUseCase,
    private val sendMessageUseCase: SendChatMessageUseCase,
    private val toggleFavoriteUseCase: ToggleChatFavoriteUseCase,
    private val toggleArchivedUseCase: ToggleChatArchivedUseCase,
    private val togglePinnedUseCase: ToggleChatPinnedUseCase,
    private val clearHistoryUseCase: ClearChatHistoryUseCase,
    private val markAsReadUseCase: MarkChatAsReadUseCase,
    private val chatPrefs: ChatPrefsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeContacts()
        observeAllMessages()
    }

    // ==================== ТАБЫ ====================

    fun switchTab(tab: ChatTab) {
        _uiState.update { state ->
            if (tab == ChatTab.FILTER) {
                state.copy(currentTab = tab, selectedChatId = null)
            } else {
                state.copy(currentTab = tab)
            }
        }
        if (tab == ChatTab.FILTER) {
            updateChatTabInfo()
        }
        viewModelScope.launch { chatPrefs.setCurrentTab(tab) }
    }

    fun selectChat(chatId: String) {
        val item = findItemById(chatId, _uiState.value.filterItems)
        _uiState.update { state ->
            val title = if (item != null) "Чат с ${item.name}" else "Чат"
            state.copy(
                selectedChatId = chatId,
                currentTab = ChatTab.CHAT,
                chatTabTitle = title,
                isChatTabEnabled = true
            )
        }
        markAsRead(chatId)
        updateFilteredMessages()
        viewModelScope.launch { chatPrefs.setSelectedChatId(chatId) }
    }

    private fun findItemById(id: String, items: List<ChatFilterItem>): ChatFilterItem? {
        for (item in items) {
            if (item.id == id) return item
            val found = findItemById(id, item.children)
            if (found != null) return found
        }
        return null
    }

    // ==================== ФИЛЬТРЫ ====================

    fun toggleArchiveSection() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.isArchiveSection) item.copy(isExpanded = !item.isExpanded) else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
    }

    fun toggleFilterItem(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun selectAllItems() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { it.copy(isChecked = true) }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun deselectAllItems() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { it.copy(isChecked = false) }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun selectFavoriteItems() {
        _uiState.update { state ->
            val favorites = collectFavorites(state.filterItems)
            val shouldSelect = favorites.isEmpty() || !favorites.all { it.isChecked }
            state.copy(filterItems = toggleFavoritesInList(state.filterItems, shouldSelect))
        }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun selectArchiveItems() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.isArchiveSection) {
                    item.copy(
                        isExpanded = true,
                        children = item.children.map { it.copy(isChecked = true) }.toImmutableList()
                    )
                } else {
                    item.copy(isChecked = false)
                }
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun toggleFavorite(itemId: String) {
        val item = findItemById(itemId, _uiState.value.filterItems) ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase(ToggleFavoriteParams(contactId = itemId, isFavorite = !item.isFavorite))
        }
    }

    fun togglePinned(itemId: String) {
        val item = findItemById(itemId, _uiState.value.filterItems) ?: return
        viewModelScope.launch {
            togglePinnedUseCase(TogglePinnedParams(contactId = itemId, isPinned = !item.isPinned))
        }
    }

    fun markAsRead(itemId: String) {
        _uiState.update { state ->
            state.copy(filterItems = markAsReadInList(itemId, state.filterItems))
        }
        viewModelScope.launch {
            markAsReadUseCase(MarkAsReadParams(contactId = itemId))
        }
    }

    fun moveToArchive(itemId: String) {
        _uiState.update { state ->
            val item = state.filterItems.find { !it.isArchiveSection && it.id == itemId }
                ?: return@update state
            val mainItems = state.filterItems.filter { !it.isArchiveSection && it.id != itemId }
            val archiveItems = state.filterItems.filter { it.isArchiveSection }.map { section ->
                section.copy(children = (section.children + item.copy(isChecked = false)).toImmutableList())
            }
            state.copy(filterItems = (mainItems + archiveItems).toImmutableList())
        }
        viewModelScope.launch {
            toggleArchivedUseCase(ToggleArchivedParams(contactId = itemId, isArchived = true))
        }
    }

    fun moveFromArchive(itemId: String) {
        _uiState.update { state ->
            val archiveSection = state.filterItems.find { it.isArchiveSection }
                ?: return@update state
            val item = archiveSection.children.find { it.id == itemId }
                ?: return@update state
            val updatedArchive = archiveSection.copy(
                children = archiveSection.children.filter { it.id != itemId }.toImmutableList()
            )
            val mainItems = (state.filterItems.filter { !it.isArchiveSection } + item)
                .sortedWith(compareBy({ !it.isPinned }, { -it.lastMessageTime }))
            state.copy(filterItems = (mainItems + updatedArchive).toImmutableList())
        }
        viewModelScope.launch {
            toggleArchivedUseCase(ToggleArchivedParams(contactId = itemId, isArchived = false))
        }
    }

    fun clearChat(itemId: String) {
        viewModelScope.launch {
            clearHistoryUseCase(ClearHistoryParams(contactId = itemId))
        }
    }

    // ==================== ПОИСК ====================

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateFilteredMessages()
    }

    // ==================== ВВОД СООБЩЕНИЙ ====================

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty()) return
        val contactId = state.selectedChatId ?: return

        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            sendMessageUseCase(SendChatMessageParams(text = text, contactId = contactId))
        }
    }

    // ==================== НАБЛЮДЕНИЕ РЕАЛЬНЫХ ДАННЫХ ====================

    private fun observeContacts() {
        viewModelScope.launch {
            observeContactsUseCase(NoParams).collect { contacts ->
                _uiState.update { state ->
                    val checkedMap = buildCheckedMap(state.filterItems)
                    val existingArchiveSection = state.filterItems.find { it.isArchiveSection }

                    val mainItems = contacts
                        .filter { !it.isArchived }
                        .sortedWith(compareBy({ !it.isPinned }, { -(it.lastMessageTime ?: 0L) }))
                        .map { it.toFilterItem(isChecked = checkedMap[it.id] ?: false) }

                    val archivedItems = contacts
                        .filter { it.isArchived }
                        .map { it.toFilterItem(isChecked = false) }

                    val archiveSection = ChatFilterItem(
                        id = "archive_section",
                        name = "Архив",
                        type = ChatType.CHANNEL,
                        isArchiveSection = true,
                        isExpanded = existingArchiveSection?.isExpanded ?: false,
                        children = archivedItems.toImmutableList()
                    )

                    state.copy(filterItems = (mainItems + archiveSection).toImmutableList())
                }
                updateChatTabInfo()
            }
        }
    }

    private fun observeAllMessages() {
        viewModelScope.launch {
            observeMessagesUseCase(ObserveChatMessagesParams(emptySet(), "")).collect { messages ->
                _uiState.update { it.copy(allMessages = messages.toImmutableList()) }
                updateFilteredMessages()
            }
        }
    }

    // ==================== ВНУТРЕННИЕ МЕТОДЫ ====================

    private fun updateFilteredMessages() {
        val state = _uiState.value
        val checkedIds = collectAllChecked(state.filterItems).map { it.id }.toSet()

        var filtered = if (state.selectedChatId != null) {
            state.allMessages.filter { it.channelId == state.selectedChatId }
        } else if (checkedIds.isNotEmpty()) {
            state.allMessages.filter { it.channelId in checkedIds }
        } else {
            state.allMessages.toList()
        }

        val query = state.searchQuery.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.text.contains(query, ignoreCase = true) ||
                        it.senderCallsign.contains(query, ignoreCase = true)
            }
        }

        filtered = filtered.sortedBy { it.sentAt }

        _uiState.update { it.copy(messages = filtered.toImmutableList()) }
    }

    private fun markAsReadInList(itemId: String, items: List<ChatFilterItem>): ImmutableList<ChatFilterItem> {
        return items.map { item ->
            when {
                item.id == itemId -> item.copy(unreadCount = 0)
                item.isArchiveSection -> item.copy(children = markAsReadInList(itemId, item.children))
                else -> item
            }
        }.toImmutableList()
    }

    private fun buildCheckedMap(items: List<ChatFilterItem>): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        fun traverse(list: List<ChatFilterItem>) {
            list.forEach { item ->
                if (!item.isArchiveSection) result[item.id] = item.isChecked
                if (item.isArchiveSection) traverse(item.children)
            }
        }
        traverse(items)
        return result
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private fun updateChatTabInfo() {
        _uiState.update { state ->
            val checkedItems = collectAllChecked(state.filterItems)
            when {
                checkedItems.isEmpty() -> state.copy(
                    chatTabTitle = "Лента",
                    isChatTabEnabled = true
                )
                checkedItems.size == 1 -> state.copy(
                    chatTabTitle = "Чат с ${checkedItems.first().name}",
                    isChatTabEnabled = true
                )
                else -> state.copy(
                    chatTabTitle = "Лента (${checkedItems.size})",
                    isChatTabEnabled = true
                )
            }
        }
    }

    private fun collectAllChecked(items: List<ChatFilterItem>): List<ChatFilterItem> {
        val result = mutableListOf<ChatFilterItem>()
        fun traverse(list: List<ChatFilterItem>) {
            list.forEach { item ->
                if (!item.isArchiveSection && item.isChecked) result.add(item)
                if (item.isArchiveSection) traverse(item.children)
            }
        }
        traverse(items)
        return result
    }

    private fun collectUnreadAll(items: List<ChatFilterItem>): Int {
        var total = 0
        fun traverse(list: List<ChatFilterItem>) {
            list.forEach { item ->
                total += item.unreadCount
                if (item.isArchiveSection) traverse(item.children)
            }
        }
        traverse(items)
        return total
    }

    private fun collectFavorites(items: List<ChatFilterItem>): List<ChatFilterItem> {
        val result = mutableListOf<ChatFilterItem>()
        fun traverse(list: List<ChatFilterItem>) {
            list.forEach { item ->
                if (item.isFavorite) result.add(item)
                if (item.isArchiveSection) traverse(item.children)
            }
        }
        traverse(items)
        return result
    }

    private fun toggleFavoritesInList(items: List<ChatFilterItem>, shouldSelect: Boolean): ImmutableList<ChatFilterItem> {
        return items.map { item ->
            if (item.isArchiveSection) {
                item.copy(children = toggleFavoritesInList(item.children, shouldSelect))
            } else if (item.isFavorite) {
                item.copy(isChecked = shouldSelect)
            } else {
                item
            }
        }.toImmutableList()
    }
}

// ==================== MAPPER ====================

private fun ChatContact.toFilterItem(isChecked: Boolean): ChatFilterItem = ChatFilterItem(
    id = id,
    name = displayName,
    type = if (type == ContactType.CHANNEL) ChatType.CHANNEL else ChatType.DIRECT_CHAT,
    isChecked = isChecked,
    isFavorite = isFavorite,
    isPinned = isPinned,
    unreadCount = unreadCount,
    lastMessageTime = lastMessageTime ?: 0L,
    lastMessagePreview = lastMessagePreview ?: "",
)
