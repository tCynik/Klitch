package ru.tcynik.klitch.presentation.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.klitch.data.chat.prefs.ChatPrefsRepository
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.chat.model.ChatContact
import ru.tcynik.klitch.domain.chat.model.ContactType
import ru.tcynik.klitch.domain.chat.usecase.ClearChatHistoryUseCase
import ru.tcynik.klitch.domain.chat.usecase.ClearHistoryParams
import ru.tcynik.klitch.domain.chat.usecase.MarkAsReadParams
import ru.tcynik.klitch.domain.chat.usecase.MarkChatAsReadUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatContactsUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatMessagesParams
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleArchivedParams
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatArchivedUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatFavoriteUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatPinnedUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleFavoriteParams
import ru.tcynik.klitch.domain.chat.usecase.TogglePinnedParams
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.klitch.presentation.feature.chat.model.ChatTab
import ru.tcynik.klitch.presentation.feature.chat.model.ChatType
import ru.tcynik.klitch.presentation.ui.UiText

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
    private val syncStateRepository: ContourSyncStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Единый источник истины для отмеченных контактов. Персистируется в DataStore. */
    private val _checkedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            val checkedIds = chatPrefs.observeCheckedIds().first()
            _checkedIds.value = checkedIds

            if (isSessionActive) {
                // Повторный вход в экран внутри той же сессии — восстанавливаем таб и чат
                val tab = chatPrefs.observeCurrentTab().first()
                val chatId = chatPrefs.observeSelectedChatId().first()
                _uiState.update {
                    it.copy(
                        currentTab = tab,
                        selectedChatId = chatId,
                        isChatTabEnabled = true,
                    )
                }
                // Пересчитываем заголовок с учётом уже загруженных контактов
                updateChatTabInfo()
            } else {
                // Первый вход за сессию — всегда на вкладку Фильтр, без выбранного чата
                isSessionActive = true
                chatPrefs.setCurrentTab(ChatTab.FILTER)
                chatPrefs.setSelectedChatId(null)
                _uiState.update {
                    it.copy(
                        currentTab = ChatTab.FILTER,
                        selectedChatId = null,
                        isChatTabEnabled = true,
                        chatTabTitle = UiText.Static(R.string.chat_tab_title_feed),
                    )
                }
            }
        }
        observeContacts()
        observeAllMessages()

        syncStateRepository.syncRequired
            .onEach { required -> _uiState.update { it.copy(syncRequired = required) } }
            .launchIn(viewModelScope)
    }

    companion object {
        /** true — если в этой сессии (жизни процесса) экран уже открывался хотя бы раз */
        private var isSessionActive = false
    }

    // ==================== ТАБЫ ====================

    fun switchTab(tab: ChatTab) {
        _uiState.update { state ->
            if (tab == ChatTab.FILTER) {
                state.copy(currentTab = tab, selectedChatId = null, selectedChatPartnerHasPKC = null)
            } else {
                state.copy(currentTab = tab)
            }
        }
        if (tab == ChatTab.FILTER) {
            updateChatTabInfo()
        }
        viewModelScope.launch {
            chatPrefs.setCurrentTab(tab)
            if (tab == ChatTab.FILTER) chatPrefs.setSelectedChatId(null)
        }
    }

    fun selectChat(chatId: String) {
        val item = findItemById(chatId, _uiState.value.filterItems)
        val pkcStatus = if (item?.type == ChatType.DIRECT_CHAT) item.partnerHasPKC else null
        _uiState.update { state ->
            val title = if (item != null)
                UiText.Dynamic(R.string.chat_tab_title_chat_with, item.name)
            else
                UiText.Static(R.string.chat_tab_title_chat)
            state.copy(
                selectedChatId = chatId,
                currentTab = ChatTab.CHAT,
                chatTabTitle = title,
                isChatTabEnabled = true,
                isSelectedChatActive = item?.isActive ?: true,
                selectedChatPartnerHasPKC = pkcStatus,
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
        val current = _checkedIds.value
        val newIds = if (itemId in current) current - itemId else current + itemId
        _checkedIds.value = newIds
        viewModelScope.launch { chatPrefs.setCheckedIds(newIds) }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun selectAllItems() {
        val allIds = _uiState.value.filterItems
            .filter { !it.isArchiveSection }
            .map { it.id }
            .toSet()
        _checkedIds.value = allIds
        viewModelScope.launch { chatPrefs.setCheckedIds(allIds) }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun deselectAllItems() {
        _checkedIds.value = emptySet()
        viewModelScope.launch { chatPrefs.setCheckedIds(emptySet()) }
        updateFilteredMessages()
        updateChatTabInfo()
    }

    fun selectFavoriteItems() {
        val favoriteIds = collectFavorites(_uiState.value.filterItems).map { it.id }.toSet()
        val shouldSelect = favoriteIds.isEmpty() || !favoriteIds.all { it in _checkedIds.value }
        val newIds = if (shouldSelect) favoriteIds else emptySet()
        _checkedIds.value = newIds
        viewModelScope.launch { chatPrefs.setCheckedIds(newIds) }
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
        if (!state.isSelectedChatActive || state.syncRequired) return
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
            combine(
                observeContactsUseCase(NoParams),
                _checkedIds,
            ) { contacts, checkedIds -> contacts to checkedIds }
            .collect { (contacts, checkedIds) ->
                if (syncStateRepository.syncRequired.value) return@collect
                _uiState.update { state ->
                    val existingArchiveSection = state.filterItems.find { it.isArchiveSection }
                    val mainItems = contacts
                        .filter { !it.isArchived }
                        .sortedWith(compareBy({ !it.isPinned }, { -(it.lastMessageTime ?: 0L) }))
                        .map { it.toFilterItem(isChecked = it.id in checkedIds) }
                    val archivedItems = contacts
                        .filter { it.isArchived }
                        .map { it.toFilterItem(isChecked = false) }
                    val archiveSection = ChatFilterItem(
                        id = "archive_section",
                        name = "",
                        type = ChatType.CHANNEL,
                        isArchiveSection = true,
                        isExpanded = existingArchiveSection?.isExpanded ?: false,
                        children = archivedItems.toImmutableList()
                    )
                    val allItems = (mainItems + archiveSection).toImmutableList()
                    val isSelectedChatActive = state.selectedChatId
                        ?.let { id -> findItemById(id, allItems) }
                        ?.isActive ?: true
                    val selectedItem = state.selectedChatId?.let { id -> findItemById(id, allItems) }
                    val pkcStatus = if (selectedItem?.type == ChatType.DIRECT_CHAT) selectedItem.partnerHasPKC else null
                    state.copy(
                        filterItems = allItems,
                        isSelectedChatActive = isSelectedChatActive,
                        selectedChatPartnerHasPKC = pkcStatus,
                    )
                }
                updateChatTabInfo()
                updateFilteredMessages()
            }
        }
    }

    private fun observeAllMessages() {
        viewModelScope.launch {
            observeMessagesUseCase(ObserveChatMessagesParams(emptySet(), "")).collect { messages ->
                if (syncStateRepository.syncRequired.value) return@collect
                _uiState.update { it.copy(allMessages = messages.toImmutableList()) }
                updateFilteredMessages()
            }
        }
    }

    // ==================== ВНУТРЕННИЕ МЕТОДЫ ====================

    private fun updateFilteredMessages() {
        val state = _uiState.value
        val checkedIds = _checkedIds.value

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

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private fun updateChatTabInfo() {
        _uiState.update { state ->
            val selectedId = state.selectedChatId
            if (selectedId != null) {
                val item = findItemById(selectedId, state.filterItems)
                return@update state.copy(
                    chatTabTitle = if (item != null)
                        UiText.Dynamic(R.string.chat_tab_title_chat_with, item.name)
                    else
                        UiText.Static(R.string.chat_tab_title_chat),
                    isChatTabEnabled = true,
                )
            }
            val checkedItems = state.filterItems
                .filter { !it.isArchiveSection && it.id in _checkedIds.value }
            when {
                checkedItems.isEmpty() -> state.copy(chatTabTitle = UiText.Static(R.string.chat_tab_title_feed), isChatTabEnabled = true)
                checkedItems.size == 1 -> state.copy(
                    chatTabTitle = UiText.Dynamic(R.string.chat_tab_title_chat_with, checkedItems.first().name),
                    isChatTabEnabled = true,
                )
                else -> state.copy(
                    chatTabTitle = UiText.Dynamic(R.string.chat_tab_title_feed_count, checkedItems.size),
                    isChatTabEnabled = true,
                )
            }
        }
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

}

// ==================== MAPPER ====================

private fun ChatContact.toFilterItem(isChecked: Boolean): ChatFilterItem = ChatFilterItem(
    id = id,
    name = displayName,
    type = if (type == ContactType.CHANNEL) ChatType.CHANNEL else ChatType.DIRECT_CHAT,
    isChecked = isChecked,
    isFavorite = isFavorite,
    isPinned = isPinned,
    isActive = isActive,
    unreadCount = unreadCount,
    lastMessageTime = lastMessageTime ?: 0L,
    lastMessagePreview = lastMessagePreview ?: "",
    partnerHasPKC = partnerHasPKC,
)
