# Chat Feature

**Статус**: ✅ Done  
**Дата завершения**: 2026-04-23  
**План**: `.claude/plans/chat-feature-plan.md`

---

## Архитектура

```
presentation/feature/chat/
    ChatScreen.kt               — UI: HorizontalPager, FilterTab, ChatTab
    ChatViewModel.kt            — real use cases, DataStore persistence
    ChatUiState.kt              — allMessages (source of truth) + messages (filtered)
    model/ChatFilterItem.kt     — ChatType, ChatTab, ChatFilterItem

domain/chat/
    model/  ChatContact, ChatMessageModel, ContactType
    repository/  ChatRepository, ChatMessageRepository
    usecase/  ObserveChatContactsUseCase, ObserveChatMessagesUseCase,
              SendChatMessageUseCase, ToggleChatFavoriteUseCase,
              ToggleChatArchivedUseCase, ToggleChatPinnedUseCase,
              ClearChatHistoryUseCase, MarkChatAsReadUseCase,
              ObserveTotalUnreadChatCountUseCase,
              IngestReceivedChatMessagesUseCase

data/chat/
    adapter/  MeshToChatAdapter        — ЕДИНСТВЕННОЕ место импорта mesh.model.*
    dto/      ChatContactDto + toDomain()
    prefs/    ChatPrefsRepository      — DataStore: tab, selectedChatId, checkedIds
    repository/  ChatRepositoryImpl, ChatMessageRepositoryImpl (SQLDelight)

di/
    ChatDataModule.kt           — все зависимости
```

---

## Ключевые решения

### Изоляция mesh-слоя

`MeshToChatAdapter` — единственное место, импортирующее `mesh.model.*`.  
Всё остальное в chat-фиче работает через `domain/chat/` интерфейсы.

### Хранение сообщений

Сообщения хранятся в **SQLDelight** (`chat_message` таблица), не в Room.  
Поле `logical_channel_id` привязывает сообщение к каналу приложения (переживает смену ноды).

```sql
CREATE TABLE chat_message (
    id                 TEXT NOT NULL PRIMARY KEY,
    logical_channel_id TEXT NOT NULL,
    sender_node_id     TEXT NOT NULL,
    text               TEXT NOT NULL,
    sent_at            INTEGER NOT NULL,
    is_self            INTEGER NOT NULL DEFAULT 0
);
```

Receive path: `MeshPacket TEXT_MESSAGE_APP` → `IngestReceivedChatMessagesUseCase.observe()` (запущен в `MainViewModel`) → `channelIndex → LogicalChannelId` → INSERT в SQLDelight.

### Контакты

Контакты читаются из Room через `PacketRepository.getContacts()` + `getContactSettings()`.  
`ContactSettings` расширен тремя колонками: `is_favorite`, `is_pinned`, `is_archived`.

Для каналов (`^all`): `channelIndex` → `LogicalChannelId` → имя канала из `LogicalChannelRepository`.  
`ChatContact.id` для каналов = `LogicalChannelId.value`.

### Состояние сообщений в ViewModel

`allMessages` — источник истины (все сообщения из SQLDelight).  
`messages` — производное (фильтрация по `selectedChatId` или `checkedIds`, + `searchQuery`).  
`updateFilteredMessages()` всегда читает из `allMessages`.

### Персистентность UI-состояния

| Что | Где |
|---|---|
| Активный таб | DataStore (`chat_current_tab`) |
| Выбранный чат | DataStore (`chat_selected_id`) |
| Отмеченные контакты | DataStore (`chat_checked_ids: Set<String>`) |
| Favourite / Pinned | Room `ContactSettings.is_favorite/is_pinned` |
| Archived | Room `ContactSettings.is_archived` |
| Unread count | Room `PacketDao` (существующий механизм) |

При первом входе в сессию — всегда вкладка Фильтр, без выбранного чата.  
При повторном открытии экрана внутри сессии — восстанавливается tab + selectedChatId.

### Архив

Физическое перемещение внутри `filterItems`: главный список + секция `isArchiveSection` с `children`.  
`moveToArchive` / `moveFromArchive` в ViewModel обновляют и UI-состояние, и Room (через `ToggleChatArchivedUseCase`).

---

## Известный техдолг

| # | Проблема | Приоритет |
|---|---|---|
| 1 | `collectUnreadAll()` дублирован в `ChatScreen` и `ChatViewModel` | 🟢 Low |
