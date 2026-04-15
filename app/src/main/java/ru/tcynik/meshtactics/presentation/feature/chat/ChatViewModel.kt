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
import ru.tcynik.meshtactics.domain.transport.model.ChatMessageModel
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatTab
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatType
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadFakeData()
    }

    // ==================== ТАБЫ ====================

    fun switchTab(tab: ChatTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun selectChat(chatId: String) {
        _uiState.update {
            it.copy(
                selectedChatId = chatId,
                currentTab = ChatTab.CHAT
            )
        }
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
    }

    fun selectAllItems() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { it.copy(isChecked = true) }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
    }

    fun deselectAllItems() {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { it.copy(isChecked = false) }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
    }

    fun selectFavoriteItems() {
        _uiState.update { state ->
            val favorites = collectFavorites(state.filterItems)
            // Если все избранные уже выбраны — снимаем выделение, иначе — выбираем
            val shouldSelect = favorites.isEmpty() || !favorites.all { it.isChecked }
            state.copy(filterItems = toggleFavoritesInList(state.filterItems, shouldSelect))
        }
        updateFilteredMessages()
    }

    fun selectArchiveItems() {
        _uiState.update { state ->
            val archiveChildren = state.filterItems
                .filter { it.isArchiveSection }
                .flatMap { it.children }
            val archiveIds = archiveChildren.map { it.id }.toSet()
            val updatedItems = state.filterItems.map { item ->
                if (item.isArchiveSection) {
                    item.copy(isExpanded = true)
                } else if (item.id in archiveIds) {
                    item.copy(isChecked = true)
                } else {
                    item.copy(isChecked = false)
                }
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
        updateFilteredMessages()
    }

    fun toggleFavorite(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId) item.copy(isFavorite = !item.isFavorite) else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
    }

    fun togglePinned(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId) item.copy(isPinned = !item.isPinned) else item
            }
            // Сортируем: закреплённые сверху
            val sortedItems = updatedItems.sortedWith(
                compareBy({ !it.isPinned }, { -it.lastMessageTime })
            )
            state.copy(filterItems = sortedItems.toImmutableList())
        }
    }

    fun markAsRead(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId) item.copy(unreadCount = 0) else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
    }

    fun moveToArchive(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId && !item.isArchiveSection) {
                    // Перемещаем в секцию архива (в реале — обновить children секции)
                    item.copy(isChecked = false)
                } else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
        }
    }

    fun clearChat(itemId: String) {
        // Для UI демо просто сбрасываем непрочитанные
        _uiState.update { state ->
            val updatedItems = state.filterItems.map { item ->
                if (item.id == itemId) item.copy(unreadCount = 0, lastMessagePreview = "Чат очищен") else item
            }
            state.copy(filterItems = updatedItems.toImmutableList())
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
        val currentState = _uiState.value
        val text = currentState.inputText.trim()
        if (text.isEmpty()) return

        val newMessage = ChatMessageModel(
            id = UUID.randomUUID().toString(),
            senderNodeId = "local-node",
            senderCallsign = "Я",
            text = text,
            sentAt = System.currentTimeMillis(),
            channelId = currentState.selectedChatId ?: "general"
        )

        _uiState.update { state ->
            val updatedMessages = (state.messages + newMessage).toImmutableList()
            state.copy(
                messages = updatedMessages,
                inputText = ""
            )
        }
    }

    // ==================== ВНУТРЕННИЕ МЕТОДЫ ====================

    private fun updateFilteredMessages() {
        val state = _uiState.value
        val checkedIds = state.filterItems.filter { it.isChecked }.map { it.id }.toSet()

        var filtered = if (state.selectedChatId != null) {
            // Если выбран конкретный чат — показываем только его сообщения
            state.messages.filter { it.channelId == state.selectedChatId }
        } else if (checkedIds.isNotEmpty()) {
            // Если выбраны чекбоксами — показываем сообщения из выбранных
            state.messages.filter { it.channelId in checkedIds }
        } else {
            // Ничего не выбрано — показываем все
            state.messages
        }

        // Фильтр по поиску
        val query = state.searchQuery.trim()
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.text.contains(query, ignoreCase = true) ||
                        it.senderCallsign.contains(query, ignoreCase = true)
            }
        }

        // Сортировка по времени (старые сверху)
        filtered = filtered.sortedBy { it.sentAt }

        _uiState.update { it.copy(messages = filtered.toImmutableList()) }
    }

    private fun loadFakeData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val hour = 3600_000L
            val minute = 60_000L

            // Фейковые фильтры (каналы, беседы + секция «Архив»)
            val archiveItems = listOf(
                ChatFilterItem("archive_001", "Группа Дельта", ChatType.CHANNEL, unreadCount = 0, lastMessageTime = now - 24 * hour, lastMessagePreview = "Миссия завершена"),
                ChatFilterItem("archive_002", "Позывной Ворон", ChatType.DIRECT_CHAT, unreadCount = 0, lastMessageTime = now - 48 * hour, lastMessagePreview = "Отбой"),
            ).toImmutableList()

            val fakeFilterItems = listOf(
                ChatFilterItem("general", "Общий канал", ChatType.CHANNEL, unreadCount = 5, lastMessageTime = now - 2 * minute, lastMessagePreview = "Кто на связи?"),
                ChatFilterItem("alpha", "Группа Альфа", ChatType.CHANNEL, unreadCount = 12, lastMessageTime = now - 5 * minute, lastMessagePreview = "Позиция Alpha-1: 55.75, 37.61"),
                ChatFilterItem("bravo", "Группа Браво", ChatType.CHANNEL, unreadCount = 0, lastMessageTime = now - 15 * minute, lastMessagePreview = "Принято, выдвигаемся"),
                ChatFilterItem("charlie", "Группа Чарли", ChatType.CHANNEL, unreadCount = 3, lastMessageTime = now - 30 * minute, lastMessagePreview = "Нужна поддержка"),
                ChatFilterItem("node_001", "Позывной Орёл", ChatType.DIRECT_CHAT, isFavorite = true, unreadCount = 2, lastMessageTime = now - 3 * minute, lastMessagePreview = "Вас вижу на карте"),
                ChatFilterItem("node_002", "Позывной Сокол", ChatType.DIRECT_CHAT, isFavorite = true, unreadCount = 0, lastMessageTime = now - 1 * hour, lastMessagePreview = "Связь отличная"),
                ChatFilterItem("node_003", "Позывной Ястреб", ChatType.DIRECT_CHAT, unreadCount = 1, lastMessageTime = now - 45 * minute, lastMessagePreview = "Батарея 20%"),
                ChatFilterItem("node_004", "Позывной Беркут", ChatType.DIRECT_CHAT, unreadCount = 0, lastMessageTime = now - 2 * hour, lastMessagePreview = "До связи"),
                ChatFilterItem(
                    id = "archive_section",
                    name = "Архив",
                    type = ChatType.CHANNEL,
                    isArchiveSection = true,
                    isExpanded = false,
                    children = archiveItems
                ),
            ).toImmutableList()

            // Фейковые сообщения
            val senders = listOf(
                "Орёл" to "node_001",
                "Сокол" to "node_002",
                "Ястреб" to "node_003",
                "Альфа-1" to "alpha",
                "Альфа-2" to "alpha",
                "Браво-1" to "bravo",
                "Чарли-1" to "charlie",
            )

            val fakeMessages = listOf(
                ChatMessageModel(UUID.randomUUID().toString(), "alpha", "Альфа-1", "Всем привет, начинаем движение", now - 60 * minute, "alpha"),
                ChatMessageModel(UUID.randomUUID().toString(), "alpha", "Альфа-2", "Принял, на связи", now - 58 * minute, "alpha"),
                ChatMessageModel(UUID.randomUUID().toString(), "general", "Орёл", "Кто на связи?", now - 55 * minute, "general"),
                ChatMessageModel(UUID.randomUUID().toString(), "node_001", "Орёл", "Вижу всех на карте", now - 50 * minute, "node_001"),
                ChatMessageModel(UUID.randomUUID().toString(), "bravo", "Браво-1", "Выдвигаемся на позицию", now - 45 * minute, "bravo"),
                ChatMessageModel(UUID.randomUUID().toString(), "charlie", "Чарли-1", "Нужна поддержка, координаты 55.75, 37.61", now - 40 * minute, "charlie"),
                ChatMessageModel(UUID.randomUUID().toString(), "general", "Сокол", "Связь проверочная", now - 35 * minute, "general"),
                ChatMessageModel(UUID.randomUUID().toString(), "node_002", "Сокол", "Связь отличная, принимаю", now - 30 * minute, "node_002"),
                ChatMessageModel(UUID.randomUUID().toString(), "alpha", "Альфа-1", "Позиция Alpha-1: 55.75, 37.61", now - 25 * minute, "alpha"),
                ChatMessageModel(UUID.randomUUID().toString(), "general", "Ястреб", "Батарея 20%, экономлю", now - 20 * minute, "general"),
                ChatMessageModel(UUID.randomUUID().toString(), "node_003", "Ястреб", "Вас вижу на карте", now - 15 * minute, "node_003"),
                ChatMessageModel(UUID.randomUUID().toString(), "charlie", "Чарли-1", "Подтверждаю координаты", now - 10 * minute, "charlie"),
                ChatMessageModel(UUID.randomUUID().toString(), "alpha", "Альфа-2", "Движемся по маршруту №2", now - 5 * minute, "alpha"),
                ChatMessageModel(UUID.randomUUID().toString(), "general", "Орёл", "Общий сбор через 30 минут", now - 2 * minute, "general"),
                ChatMessageModel(UUID.randomUUID().toString(), "node_001", "Орёл", "Понял, буду", now - 1 * minute, "node_001"),
            ).toImmutableList()

            _uiState.update {
                it.copy(
                    filterItems = fakeFilterItems,
                    messages = fakeMessages
                )
            }
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

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
