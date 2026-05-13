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

## Inactive contour state

Когда `Contour.isActive = false`, чат-экран переходит в режим только для чтения.

### Поведение

- Контакт остаётся в списке Filter Tab на своей позиции (сортировка не меняется)
- Строка контакта визуально приглушена: `Modifier.alpha(0.45f)` применяется ко всему контенту строки **кроме unread-бейджа** — он всегда на полной альфе
- На Chat Tab строка ввода заменяется баннером `InactiveContourBanner` ("Контур неактивен")
- `sendMessage()` в ViewModel имеет guard: `if (!state.isSelectedChatActive) return`
- `PRIVATE`-контакты всегда `isActive = true`

### Цепочка propagation

```
Contour.isActive (domain/channel)
  → MeshToChatAdapter.observeContactsAsFlow()  ← CHANNEL branch only
  → ChatContactDto.isActive
  → ChatContactDto.toDomain() → ChatContact.isActive
  → toFilterItem() → ChatFilterItem.isActive
  → observeContacts() → ChatUiState.isSelectedChatActive
```

`observeContacts()` в ViewModel пересчитывает `isSelectedChatActive` при каждом обновлении списка контуров — смена `isActive` отображается live, пока чат открыт.

---

## Non-obvious decisions

### Инвариант contactId для приватных чатов

`ChatContact.id` для PRIVATE контактов всегда имеет формат `"${channel}!nodeId"` (например `"3!abc123"`). Канал берётся из `node.channel` при создании контакта (`buildPrivateCandidates`).

**Критично**: `sendMessage()` должен использовать `parsedChannel` из самого contactId — и никогда не делать live lookup в `nodeRepository.getNode().channel` для переопределения канала. Если канал при отправке отличается от канала в contactId, то `dbContactKey` расходится с `selectedChatId`, и сообщения становятся невидимы в фильтре `updateFilteredMessages`.

Правило: **channel кодируется в contactId один раз при создании контакта**, и дальше только он используется.

---

## Известный техдолг

| # | Проблема | Приоритет |
|---|---|---|
| 1 | `collectUnreadAll()` дублирован в `ChatScreen` и `ChatViewModel` | 🟢 Low |
