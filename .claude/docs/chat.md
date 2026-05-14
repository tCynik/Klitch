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

`ChatContact.id` для PRIVATE контактов всегда имеет формат `"${channel}!nodeId"`. Канал определяется один раз при создании контакта и не меняется в дальнейшем.

**Правило выбора канала (`buildPrivateCandidates`):**
1. Если есть история (contactKey в Room): берётся из истории. При конфликте (есть и `"0!B"` и `"8!B"`) — **PKC (channel=8) побеждает** (sort + associateBy).
2. Если нет истории (новая нода): если оба узла `hasPKC == true` → `"8!nodeId"` (PKC); иначе → `"0!nodeId"` (канал 0 по умолчанию).

**Почему важно**: Meshtastic (firmware ≥ 2.5) автоматически отправляет DM через PKC (channel=8) когда оба узла имеют public key. `MeshDataMapper` хранит такие пакеты с `channel = PKC_CHANNEL_INDEX`. Если наш contactId = `"0!B"` а входящий DM = `"8!B"`, они хранятся в разных `logical_channel_id` в SQLDelight — сообщения невидимы в фильтре.

**Критично для sendMessage()**: использовать `parsedChannel` из contactId напрямую — никогда не делать live lookup в `nodeRepository.getNode().channel`. Channel закодирован в contactId и отражает реальный канал коммуникации.

---

## Известный техдолг

| # | Проблема | Приоритет |
|---|---|---|
| 1 | `collectUnreadAll()` дублирован в `ChatScreen` и `ChatViewModel` | 🟢 Low |
| 2 | DM не отображаются — PKC key exchange issue на firmware уровне | 🟡 Medium |

---

## Диагностика DM (личных сообщений) — 2026-05-14

### Симптом
Сообщения в каналах работают. DM: нет ни пуша, ни сообщения в чате.

### Трассировка пути

Добавлены диагностические логи (`tag:MeshDataHandler`, `tag:IngestChatMessages`, `tag:ChatAdapter`):

**Отправка (doSend):** работает корректно.
```
DBG doSend: to=!9e9f2690 channel=0 contactKey=0!9e9f2690
DBG doSend: after sendData packetId=... status=QUEUED
```

**Приём на принимающем устройстве** (`!9e7676a0`, myNodeNum=2658563744):
```
DBG fromRadio: portnum=null from=2661230224 to=2658563744 pki=false
```

`portnum=null` = `packet.decoded == null` — firmware не расшифровал DM.

**Точка дропа в коде:**
```kotlin
// MeshMessageProcessorImpl.processReceivedMeshPacket(), line ~184
val decoded = packet.decoded ?: return   // ← дроп здесь, до handleReceivedData
```

### Root cause

DM приходит на радио принимающей ноды (подтверждено `ROUTING ACK` в логах от отправителя обратно к получателю). Однако **firmware не может расшифровать payload** (`decoded=null`).

Broadcast-пакеты (позиции, канальные сообщения) от той же ноды расшифровываются нормально через PSK channel 0.

**Причина:** Firmware Meshtastic автоматически применяет PKC-шифрование для DM между нодами, у которых есть public key. При сломанном или незавершённом PKC key exchange — decrypt fail. Поле `pki=false` в `FromRadio` firmware выставляет даже при PKC-fail.

### Что было проверено в app-коде

- `sendMessage` / `doSend`: канал кодируется корректно из contactId, no live lookup ✓
- `buildPrivateCandidates`: PKC-aware fallback (`"8!nodeId"` vs `"0!nodeId"`) ✓
- `resolveNodeNum`: hex→Int конвертация корректна ✓
- `IngestReceivedChatMessagesUseCase`: DM routing через `logicalChannelId = contactKey` ✓
- `ChatViewModel.updateFilteredMessages`: фильтр по `channelId == selectedChatId` ✓

App-код корректен. Проблема ниже уровня нашего приложения.

### TODO

- [ ] Дождаться починки PKC в официальном приложении / firmware Meshtastic
- [ ] При возобновлении: проверить состояние key exchange на тестовых нодах (Settings → Radio → User → public key присутствует на обеих нодах и видна в NodeDB у партнёра)
- [ ] Рассмотреть fallback: если DM получен с `decoded=null` и `pki_encrypted=false` — показывать уведомление "зашифрованное сообщение (PKC)"
- [ ] Убрать debug-логи из `MeshDataHandlerImpl` и `MeshMessageProcessorImpl` перед релизом

### Добавленные debug-логи (НЕ закоммичены, удалить перед релизом)

- `MeshDataHandlerImpl.handleReceivedData()` — лог всех входящих пакетов
- `MeshDataHandlerImpl.handleTextMessage()` — лог text-пакетов
- `MeshMessageProcessorImpl.handleFromRadio()` — лог до proto-decode включая `portnum=null` случаи
- `MeshToChatAdapter.doSend()` — лог отправки DM
