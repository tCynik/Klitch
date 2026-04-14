# План разработки фичи Chat (исправленный)

## Архитектурный контекст
- **UI**: Jetpack Compose + Material3
- **Навигация**: Navigation Compose (type-safe), `Route.Chat` уже существует
- **DI**: Koin 4.x
- **Данные**:
  - **Room** (`mesh/`) — mesh-данные: сообщения (Packet), настройки контактов (ContactSettings), узлы, логи
  - **SQLDelight** (`shared/`) — тактические узлы для KMP-карты (1 таблица `Node`, НЕ используется для чатов)
  - **DataStore** (`mesh/`) — mesh/UI настройки, конфиги, protobuf
  - **Multiplatform Settings** (`shared/`) — простые настройки (3 ключа: device_id, marker_size_level)
- **Существующие сообщения**: `Packet` entity, `PacketDao`, `ContactSettings`, `Message`, `DataPacket`, `MeshMessagingRepository`, `ObserveMessagesUseCase`, `SendMeshMessageUseCase` в mesh-слое

## Существующая архитектура (важно)

Проект использует **`app/` как слой presentation+domain+data**, а не `shared/`:
- `app/domain/` — domain-интерфейсы (репозитории, use cases, модели) — **это каноническое место для domain в этом проекте**
- `app/data/` — реализации репозиториев, mapper'ы, DTO, adapter'ы
- `shared/` — KMP-слой с SQLDelight (только тактические данные)
- `mesh/` — Meshtastic-протокол (Room, BLE, пакеты, сервис) — использует **аннотированный Koin** (`@Single`, `@Module`)

**Dependency Law (фактическая в проекте):**
```
app/presentation/          ← зависит от app/domain/
    ↓
app/domain/                ← чистый Kotlin, НЕ зависит от mesh/
    ↓
app/data/                  ← реализует app/domain/ интерфейсы
    ↓
mesh/                      ← Meshtastic-протокол (Room, BLE, пакеты)
```

## Ключевое правило: chat-фича НЕ импортирует mesh-модели

> **Chat-фича использует СВОИ DTO и СВОИ domain-модели.**
> Конвертация из mesh-моделей в Chat-DTO происходит в **единственном** месте — `MeshToChatAdapter` в `app/data/chat/adapter/`.
> Ни domain-слой, ни mapper'ы, ни ViewModel, ни UI — НЕ импортируют `ru.tcynik.meshtactics.mesh.model.*`.

```
mesh-модели (DataPacket, Message, Contact)
    ↓
MeshToChatAdapter          ← ЕДИНСТВЕННОЕ место, импортирующее mesh-модели
    ↓ конвертирует mesh → DTO
Chat-DTO (ChatContactDto, ChatMessageDto)
    ↓
Mapper (dto.toDomain())
    ↓
Domain-модели (ChatContact, ChatMessage)
    ↓
ViewModel → UI
```

## Уточнения от пользователя
- Клик на айтем → показывает сообщения **только этого контакта**, чекбоксы не меняются
- "Выбрать всё" и "Избранное" — **отмечают чекбоксы**, "Архив" — **заглушка**
- Логика архива пока **не реализуется**, но интерфейс **должен быть** (`toggleArchived`)
- Сообщения в общем потоке **перемешаны по времени** (как мессенджер)
- Стили: **пузыри как в мессенджере** (исходящие справа, входящие слева)
- Реакции: **нет**
- Поиск: **по отфильтрованным** сообщениям

---

## ФАЗА 1: Domain-слой (app/domain/chat/)

### 1.1. Domain-модели (app/domain/chat/model/)

#### `ChatContact.kt` — модель контакта для чата
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

/**
 * Domain-модель контакта для вкладки "Фильтр".
 * Полностью независима от mesh.model.Contact и mesh.model.ContactSettings.
 */
data class ChatContact(
    val id: String,                    // contactKey
    val displayName: String,
    val type: ContactType,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageTime: Long? = null,
    val lastMessagePreview: String? = null,
)

enum class ContactType {
    CHANNEL,        // каналы (например "^all")
    PRIVATE,        // личные беседы
}
```

> ⚠️ **`isArchived` включён** — поле есть в модели, но логика архива — заглушка в MVP.
> ⚠️ **`ChatContactItem` НЕ создаётся** — UI-состояние чекбокса живёт в `UiState.selectedContactIds`.

#### `ChatMessage.kt` — модель сообщения для чата
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

/**
 * Domain-модель сообщения для вкладки "Чат".
 * Полностью независима от mesh.model.Message и mesh.model.DataPacket.
 */
data class ChatMessage(
    val id: Long,                      // уникальный ID
    val contactId: String,             // contactKey (какому контакту принадлежит)
    val senderName: String,            // имя отправителя
    val text: String,                  // текст сообщения
    val timestamp: Long,               // время в millis
    val isOutgoing: Boolean,           // true = исходящее
    val isRead: Boolean = false,       // прочитано ли
    val deliveryStatus: ChatMessageDelivery = ChatMessageDelivery.Pending,
)

enum class ChatMessageDelivery {
    Pending,    // ожидает отправки
    Sent,       // отправлено на радио
    Delivered,  // доставлено
    Failed,     // ошибка
}
```

#### `ChatTab.kt`
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

enum class ChatTab(val label: String) {
    FILTER("Фильтр"),
    CHAT("Чат"),
}
```

### 1.2. Params для Use Cases (app/domain/chat/usecase/)

```kotlin
package ru.tcynik.meshtactics.domain.chat.usecase

/** Params: Set<ContactId> для отображения сообщений выбранных контактов */
data class ObserveChatMessagesParams(
    val contactIds: Set<String>,
    val searchQuery: String = "",
)

data class SendChatMessageParams(
    val text: String,
    val contactId: String,
    val channel: Int = 0,
)

data class ToggleFavoriteParams(
    val contactId: String,
    val isFavorite: Boolean,
)

data class ToggleArchivedParams(
    val contactId: String,
    val isArchived: Boolean,
)

data class ClearHistoryParams(
    val contactId: String,
)

data class MarkAsReadParams(
    val contactId: String,
)

data class SearchMessagesParams(
    val contactIds: Set<String>,
    val query: String,
)
```

### 1.3. Use Cases (app/domain/chat/usecase/)

| Use Case | Базовый класс | Описание |
|----------|---------------|----------|
| `ObserveChatContactsUseCase` | `FlowUseCase<NoParams, List<ChatContact>>` | Flow списка контактов |
| `ObserveChatMessagesUseCase` | `FlowUseCase<ObserveChatMessagesParams, List<ChatMessage>>` | Flow сообщений по contactId(s) |
| `SendChatMessageUseCase` | `UseCase<SendChatMessageParams, Unit>` | Отправка сообщения |
| `ToggleChatFavoriteContactUseCase` | `UseCase<ToggleFavoriteParams, Unit>` | Переключить избранное |
| `ToggleChatArchivedContactUseCase` | `UseCase<ToggleArchivedParams, Unit>` | Переключить архив |
| `ClearChatHistoryUseCase` | `UseCase<ClearHistoryParams, Unit>` | Очистить историю |
| `MarkChatAsReadUseCase` | `UseCase<MarkAsReadParams, Unit>` | Пометить как прочитанное |
| `SearchChatMessagesUseCase` | plain `operator fun invoke` | Синхронный поиск (НЕ extends UseCase) |

> ⚠️ **`SearchChatMessagesUseCase`** — синхронная операция, поэтому НЕ наследует `UseCase` (по правилу: «Do NOT use `UseCase` for synchronous operations — use plain `operator fun invoke`»).

### 1.4. Репозиторий интерфейс (app/domain/chat/repository/ChatRepository.kt)

```kotlin
package ru.tcynik.meshtactics.domain.chat.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ChatMessage

/**
 * Репозиторий для фичи Chat.
 * Работает ТОЛЬКО с domain-моделями. НЕ импортирует mesh-модели.
 * Источник данных — MeshToChatAdapter в data-слое.
 */
interface ChatRepository {
    fun observeContacts(): Flow<List<ChatContact>>
    fun observeMessages(contactIds: Set<String>, searchQuery: String = ""): Flow<List<ChatMessage>>
    suspend fun sendMessage(text: String, contactId: String, channel: Int)
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean)
    suspend fun togglePinned(contactId: String, isPinned: Boolean)
    suspend fun toggleArchived(contactId: String, isArchived: Boolean)
    suspend fun clearHistory(contactId: String)
    suspend fun markAsRead(contactId: String)
    fun searchMessages(contactIds: Set<String>, query: String): List<ChatMessage>
}
```

---

## ФАЗА 2: Data-слой (app/data/chat/)

### 2.1. DTO (app/data/chat/dto/)

> ⚠️ **Собственные DTO** — НЕ импортируют mesh-модели. Чистые data-классы.

#### `ChatContactDto.kt`
```kotlin
package ru.tcynik.meshtactics.data.chat.dto

import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ContactType

/**
 * DTO контакта. Чистый data-класс, НЕ зависит от mesh-моделей.
 */
data class ChatContactDto(
    val id: String,
    val shortName: String,
    val longName: String,
    val type: ContactType,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageTime: Long? = null,
    val lastMessagePreview: String? = null,
)

fun ChatContactDto.toDomain(): ChatContact = ChatContact(
    id = id,
    displayName = longName.ifBlank { shortName },
    type = type,
    isFavorite = isFavorite,
    isPinned = isPinned,
    isArchived = isArchived,
    unreadCount = unreadCount,
    lastMessageTime = lastMessageTime,
    lastMessagePreview = lastMessagePreview,
)
```

#### `ChatMessageDto.kt`
```kotlin
package ru.tcynik.meshtactics.data.chat.dto

import ru.tcynik.meshtactics.domain.chat.model.ChatMessage
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery

/**
 * DTO сообщения. Чистый data-класс, НЕ зависит от mesh-моделей.
 */
data class ChatMessageDto(
    val id: Long,
    val contactId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isRead: Boolean = false,
    val deliveryStatus: ChatMessageDelivery = ChatMessageDelivery.Pending,
)

fun ChatMessageDto.toDomain(): ChatMessage = ChatMessage(
    id = id,
    contactId = contactId,
    senderName = senderName,
    text = text,
    timestamp = timestamp,
    isOutgoing = isOutgoing,
    isRead = isRead,
    deliveryStatus = deliveryStatus,
)
```

### 2.2. Adapter (app/data/chat/adapter/)

> ⚠️ **ЕДИНСТВЕННОЕ место в chat-фиче, которое импортирует mesh-модели.**
> Конвертирует mesh-модели → Chat-DTO.

#### `MeshToChatAdapter.kt`
```kotlin
package ru.tcynik.meshtactics.data.chat.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.dto.ChatContactDto
import ru.tcynik.meshtactics.data.chat.dto.ChatMessageDto
import ru.tcynik.meshtactics.data.chat.dto.toChatDelivery
import ru.tcynik.meshtactics.domain.chat.model.ContactType
import ru.tcynik.meshtactics.mesh.model.Contact
import ru.tcynik.meshtactics.mesh.model.ContactSettings
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.model.Message
import ru.tcynik.meshtactics.mesh.model.MessageStatus
import ru.tcynik.meshtactics.mesh.model.Node
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

/**
 * Адаптер: конвертирует mesh-модели → Chat-DTO.
 * Это ЕДИНСТВЕННОЕ место в chat-фиче, которое импортирует mesh-модели.
 * Все остальные компоненты chat-фичи работают только с DTO и domain-моделями.
 */
class MeshToChatAdapter(
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
) {

    fun observeContactsAsFlow(): Flow<List<ChatContactDto>> =
        combine(
            packetRepository.getContacts(),
            packetRepository.getContactSettings(),
        ) { contactsMap, settingsMap ->
            contactsMap.values.map { dataPacket ->
                val contact = dataPacket.toContact()
                val settings = settingsMap[contact.contactKey]
                contact.toDto(settings)
            }
        }

    fun observeMessagesAsFlow(
        contactIds: Set<String>,
        searchQuery: String = "",
    ): Flow<List<ChatMessageDto>> =
        if (contactIds.isEmpty()) {
            kotlinx.coroutines.flow.emptyFlow()
        } else {
            combine(
                contactIds.map { contactId ->
                    packetRepository.getMessagesFrom(
                        contact = contactId,
                        getNode = { id -> nodeRepository.getNodeById(id) ?: Node.UNKNOWN },
                    ).map { messages ->
                        messages.map { msg -> msg.toDto(contactId) }
                    }
                }
            ) { messageLists ->
                messageLists
                    .flatten()
                    .sortedBy { it.timestamp }
                    .filter { dto ->
                        searchQuery.isBlank() || dto.text.contains(searchQuery, ignoreCase = true)
                    }
            }
        }

    suspend fun sendMessage(text: String, contactId: String, channel: Int) {
        // Делегирует в mesh-слой через PacketRepository
        // Реализация зависит от того, как mesh отправляет сообщения
    }

    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        packetRepository.setFavorite(contactId, isFavorite)
    }

    suspend fun togglePinned(contactId: String, isPinned: Boolean) {
        packetRepository.setPinned(contactId, isPinned)
    }

    suspend fun toggleArchived(contactId: String, isArchived: Boolean) {
        packetRepository.setArchived(contactId, isArchived)
    }

    suspend fun clearHistory(contactId: String) {
        packetRepository.deleteContacts(listOf(contactId))
    }

    suspend fun markAsRead(contactId: String) {
        packetRepository.clearUnreadCount(contactId, System.currentTimeMillis())
    }

    fun searchMessages(contactIds: Set<String>, query: String): List<ChatMessageDto> {
        // Синхронный поиск — реализуется при необходимости
        return emptyList()
    }
}

// ---- Extension-функции конвертации mesh → DTO ----

private fun DataPacket.toContact(): Contact {
    // Конвертация DataPacket → Contact (берём из dataPacket поля)
    return Contact(
        contactKey = contactKey,
        shortName = shortName ?: "",
        longName = longName ?: "",
        lastMessageTime = lastMessageTime,
        lastMessageText = lastMessageText,
        unreadCount = unreadCount,
        messageCount = messageCount,
        isMuted = isMuted,
        isUnmessageable = isUnmessageable,
    )
}

private fun Contact.toDto(settings: ContactSettings? = null): ChatContactDto {
    val type = if (contactKey.contains("^all") || contactKey.startsWith("^")) {
        ContactType.CHANNEL
    } else {
        ContactType.PRIVATE
    }
    return ChatContactDto(
        id = contactKey,
        shortName = shortName,
        longName = longName,
        type = type,
        isFavorite = settings?.isFavorite ?: false,
        isPinned = settings?.isPinned ?: false,
        isArchived = settings?.isArchived ?: false,
        unreadCount = unreadCount,
        lastMessageTime = lastMessageTime,
        lastMessagePreview = lastMessageText,
    )
}

private fun Message.toDto(contactId: String): ChatMessageDto = ChatMessageDto(
    id = uuid,
    contactId = contactId,
    senderName = node.user.long_name.ifBlank { node.user.shortName.ifBlank { node.user.id } },
    text = text,
    timestamp = receivedTime,
    isOutgoing = fromLocal,
    isRead = read,
    deliveryStatus = status.toChatDelivery(),
)

private fun MessageStatus?.toChatDelivery() = when (this) {
    MessageStatus.QUEUED -> ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Pending
    MessageStatus.ENROUTE -> ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Sent
    MessageStatus.DELIVERED, MessageStatus.RECEIVED, MessageStatus.SFPP_CONFIRMED ->
        ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Delivered
    MessageStatus.ERROR -> ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Failed
    else -> ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Pending
}

// Alias для удобства
private fun ru.tcynik.meshtactics.domain.chat.model.ChatMessageDelivery.Companion.toChatDelivery(): Nothing =
    error("Use MessageStatus?.toChatDelivery() extension")
```

### 2.3. Repository реализация (app/data/chat/repository/ChatRepositoryImpl.kt)

> ⚠️ **НЕ импортирует mesh-модели.** Работает только с `MeshToChatAdapter` и DTO.

```kotlin
package ru.tcynik.meshtactics.data.chat.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.meshtactics.data.chat.dto.toDomain
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ChatMessage
import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository

/**
 * ChatRepositoryImpl НЕ импортирует mesh-модели.
 * Вся конвертация из mesh происходит в MeshToChatAdapter.
 */
class ChatRepositoryImpl(
    private val adapter: MeshToChatAdapter,
) : ChatRepository {

    override fun observeContacts(): Flow<List<ChatContact>> =
        adapter.observeContactsAsFlow()
            .map { dtos -> dtos.map { it.toDomain() } }

    override fun observeMessages(
        contactIds: Set<String>,
        searchQuery: String,
    ): Flow<List<ChatMessage>> =
        adapter.observeMessagesAsFlow(contactIds, searchQuery)
            .map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun sendMessage(text: String, contactId: String, channel: Int) {
        adapter.sendMessage(text, contactId, channel)
    }

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        adapter.toggleFavorite(contactId, isFavorite)
    }

    override suspend fun togglePinned(contactId: String, isPinned: Boolean) {
        adapter.togglePinned(contactId, isPinned)
    }

    override suspend fun toggleArchived(contactId: String, isArchived: Boolean) {
        adapter.toggleArchived(contactId, isArchived)
    }

    override suspend fun clearHistory(contactId: String) {
        adapter.clearHistory(contactId)
    }

    override suspend fun markAsRead(contactId: String) {
        adapter.markAsRead(contactId)
    }

    override fun searchMessages(contactIds: Set<String>, query: String): List<ChatMessage> =
        adapter.searchMessages(contactIds, query)
            .map { it.toDomain() }
}
```

---

## ФАЗА 3: Сохранение состояния (Room в mesh-модуле)

> ⚠️ **SQLDelight НЕ используется для чатов**. Он зарезервирован для тактических данных KMP-слоя.
> Все данные чатов хранятся в **Room** (`mesh/` модуль).

### 3.1. Расширить ContactSettings (mesh/database/entity/Packet.kt)

В `ContactSettings` entity добавить:
```kotlin
@ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
@ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
@ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
```

### 3.2. Миграция БД
- Версия: текущая → +1
- `ALTER TABLE contact_settings ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE contact_settings ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE contact_settings ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0`
- На время разработки: `fallbackToDestructiveMigration()` до тестирования миграции

### 3.3. PacketDao — новые методы

```kotlin
@Query("UPDATE contact_settings SET is_favorite = :isFavorite WHERE contact_key = :contactKey")
suspend fun updateFavorite(contactKey: String, isFavorite: Boolean)

@Query("UPDATE contact_settings SET is_pinned = :isPinned WHERE contact_key = :contactKey")
suspend fun updatePinned(contactKey: String, isPinned: Boolean)

@Query("UPDATE contact_settings SET is_archived = :isArchived WHERE contact_key = :contactKey")
suspend fun updateArchived(contactKey: String, isArchived: Boolean)

// Сортировка: закреплённые → обычные по времени
@Query("""
    SELECT cs.*, p.* FROM contact_settings cs
    LEFT JOIN packet p ON cs.contact_key = p.contact_key
    WHERE p.received_time = (
        SELECT MAX(received_time) FROM packet
        WHERE contact_key = cs.contact_key AND port_num = 1 AND filtered = 0
    )
    ORDER BY cs.is_pinned DESC, p.received_time DESC
""")
fun getContactsWithLastMessage(): Flow<List<ContactWithSettingsAndLastMessage>>
```

### 3.4. PacketRepository (mesh интерфейс) — новые методы

В `mesh/repository/PacketRepository.kt`:
```kotlin
suspend fun setFavorite(contactKey: String, isFavorite: Boolean)
suspend fun setPinned(contactKey: String, isPinned: Boolean)
suspend fun setArchived(contactKey: String, isArchived: Boolean)
```

В `mesh/data/repository/PacketRepositoryImpl.kt` — делегирование в DAO.

---

## ФАЗА 4: UI "Фильтр"

### 4.1. Структура компонентов (app/presentation/feature/chat/components/filter/)

```
FilterTabContent.kt          — основной экран вкладки
FilterTopBar.kt              — кнопки: Выбрать всё, Избранное, Архив (заглушка)
ContactList.kt               — LazyColumn контактов
ContactListItem.kt           — один элемент списка
ContactDropdownMenu.kt       — выпадающее меню (⋮)
```

### 4.2. ContactListItem
```
┌────────────────────────────────────────────────────┐
│ ☑  ★  Название канала    12   14:30         ⋮     │
└────────────────────────────────────────────────────┘
```
- **Checkbox** (слева) — состояние чекбокса (из `UiState.selectedContactIds`)
- **Star** (избранное) — видна если `contact.isFavorite = true`
- **Archived badge** — если `contact.isArchived = true` (заглушка, визуально)
- **Название** — `contact.displayName`
- **Unread badge** — если `contact.unreadCount > 0`
- **Время** — форматированное `contact.lastMessageTime`
- **DropdownMenu** (⋮):
  - Закрепить / Открепить
  - В избранное / Убрать из избранного
  - Пометить как прочитанное
  - В архив / Из архива (переключает `isArchived` через `toggleArchived`)
  - Очистить (с диалогом подтверждения)

### 4.3. FilterTopBar
```
[ Выбрать всё ]  [ Избранное ]  [ Архив ]
```
- "Выбрать всё" — отмечает все чекбоксы
- "Избранное" — отмечает чекбоксами только избранные
- "Архив" — заглушка (Snackbar "В разработке")

### 4.4. Логика сортировки
- Закреплённые сверху
- Остальные по `lastMessageTime` (новые сверху)

---

## ФАЗА 5: UI "Чат"

### 5.1. Структура компонентов (app/presentation/feature/chat/components/chat/)

```
ChatTabContent.kt            — Scaffold вкладки
ChatSearchBar.kt             — TopAppBar с поиском
MessageList.kt               — LazyColumn сообщений
MessageBubble.kt             — пузырь сообщения (входящие/исходящие)
ChatInputBar.kt              — BottomAppBar (TextField + кнопка)
```

### 5.2. Layout
```
┌─────────────────────────────────────────┐
│  [Поиск по сообщениям...]               │ ← ChatSearchBar
├─────────────────────────────────────────┤
│                                         │
│  ╭────────────╮                         │ ← Входящее (слева)
│  │ Привет!    │                         │
│  ╰────────────╯                                  │
│         ╭────────────╮                   │
│         │   Привет!  │                   │ ← Исходящее (справа)
│         ╰────────────╯                   │
│                                         │
├─────────────────────────────────────────┤
│  [Введите сообщение...]        [➤]      │ ← ChatInputBar
└─────────────────────────────────────────┘
```

### 5.3. MessageBubble
- **Входящие** (`isOutgoing = false`): выровнены влево, цвет фона `surfaceVariant`
- **Исходящие** (`isOutgoing = true`): выровнены вправо, цвет фона `primaryContainer`
- Текст + время внутри пузыря
- Сортировка по времени (старые ↑)
- `LazyColumn(reverseLayout = false)` с начальным скроллом вниз

### 5.4. ChatInputBar
- `TextField` + `IconButton` (отправить)
- `Modifier.imePadding()` для клавиатуры
- При появлении клавиатуры — строка ввода поднимается

### 5.5. Логика
- При клике на контакт → `activeContactId` → только его сообщения
- При чекбоксах → сообщения всех выбранных в общем потоке
- Поиск фильтрует по тексту (только в отфильтрованных)

---

## ФАЗА 6: Навигация и свайпы

### 6.1. TabRow
```kotlin
TabRow(selectedTabIndex = activeTab.ordinal) {
    ChatTab.entries.forEach { tab ->
        Tab(
            selected = activeTab == tab,
            onClick = { onTabSelected(tab) },
            text = { Text(tab.label) },
        )
    }
}
```

### 6.2. Свайпы
- `Modifier.pointerInput` + `detectHorizontalDragGestures`
- Свайп влево на "Фильтр" → переключить на "Чат"
- Свайп вправо на "Чат" → переключить на "Фильтр"
- Свайп вправо на "Фильтр" → `onNavigateBack()` (главный экран)

---

## ФАЗА 7: ViewModel + UiState + DI

### 7.1. ChatUiState (исправленный)

```kotlin
package ru.tcynik.meshtactics.presentation.feature.chat

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ChatMessage
import ru.tcynik.meshtactics.domain.chat.model.ChatTab

/**
 * ✅ Чекбоксы хранятся в UiState (selectedContactIds),
 * а НЕ в domain-модели ChatContactItem.
 */
data class ChatUiState(
    val activeTab: ChatTab = ChatTab.FILTER,
    val contacts: ImmutableList<ChatContact> = persistentListOf(),
    val messages: ImmutableList<ChatMessage> = persistentListOf(),
    val selectedContactIds: ImmutableSet<String> = persistentSetOf(),
    val activeContactId: String? = null,   // при клике на контакт
    val searchQuery: String = "",
    val inputText: String = "",
    val showAllChecked: Boolean = false,
    val showFavoritesChecked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showClearConfirmation: Boolean = false,
    val snackbarMessage: String? = null,
)
```

### 7.2. ChatViewModel (app/presentation/feature/chat/ChatViewModel.kt)

```kotlin
package ru.tcynik.meshtactics.presentation.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.chat.model.ChatTab
import ru.tcynik.meshtactics.domain.chat.usecase.*
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ChatViewModel(
    private val observeContactsUseCase: ObserveChatContactsUseCase,
    private val observeMessagesUseCase: ObserveChatMessagesUseCase,
    private val sendMessageUseCase: SendChatMessageUseCase,
    private val toggleFavoriteUseCase: ToggleChatFavoriteContactUseCase,
    private val toggleArchivedUseCase: ToggleChatArchivedContactUseCase,
    private val clearHistoryUseCase: ClearChatHistoryUseCase,
    private val markAsReadUseCase: MarkChatAsReadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeContacts()
        observeMessages()
    }

    private fun observeContacts() {
        viewModelScope.launch {
            observeContactsUseCase(NoParams)
                .catch { emit(emptyList()) }
                .collect { contacts ->
                    _uiState.update {
                        it.copy(contacts = contacts.toImmutableList())
                    }
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.selectedContactIds },
                _uiState.map { it.searchQuery },
            ) { ids, query -> ids to query }
                .collect { (contactIds, query) ->
                    if (contactIds.isEmpty()) {
                        _uiState.update { it.copy(messages = persistentListOf()) }
                        return@collect
                    }
                    observeMessagesUseCase(
                        ObserveChatMessagesParams(contactIds, query)
                    ).catch { emit(emptyList()) }
                        .collect { messages ->
                            _uiState.update {
                                it.copy(messages = messages.toImmutableList())
                            }
                        }
                }
        }
    }

    // Навигация
    fun onTabSelected(tab: ChatTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onNavigateBack() { /* callback через NavGraph */ }

    // Фильтр
    fun onContactChecked(contactId: String) {
        _uiState.update { state ->
            val newIds = if (state.selectedContactIds.contains(contactId)) {
                state.selectedContactIds - contactId
            } else {
                state.selectedContactIds + contactId
            }
            state.copy(selectedContactIds = newIds)
        }
    }

    fun onContactClicked(contactId: String) {
        _uiState.update {
            it.copy(
                activeContactId = contactId,
                selectedContactIds = persistentSetOf(contactId),
            )
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            val allIds = state.contacts.map { it.id }.toSet()
            state.copy(selectedContactIds = allIds.toImmutableSet())
        }
    }

    fun onSelectFavorites() {
        _uiState.update { state ->
            val favoriteIds = state.contacts
                .filter { it.isFavorite }
                .map { it.id }
                .toSet()
            state.copy(selectedContactIds = favoriteIds.toImmutableSet())
        }
    }

    fun onToggleFavorite(contactId: String) {
        viewModelScope.launch {
            val contact = _uiState.value.contacts.find { it.id == contactId } ?: return@launch
            toggleFavoriteUseCase(ToggleFavoriteParams(contactId, !contact.isFavorite))
        }
    }

    fun onToggleArchived(contactId: String) {
        viewModelScope.launch {
            val contact = _uiState.value.contacts.find { it.id == contactId } ?: return@launch
            toggleArchivedUseCase(ToggleArchivedParams(contactId, !contact.isArchived))
        }
    }

    fun onMarkAsRead(contactId: String) {
        viewModelScope.launch {
            markAsReadUseCase(MarkAsReadParams(contactId))
        }
    }

    fun onClearHistory(contactId: String) {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }

    fun confirmClearHistory() {
        viewModelScope.launch {
            val contactId = _uiState.value.activeContactId ?: return@launch
            clearHistoryUseCase(ClearHistoryParams(contactId))
            _uiState.update { it.copy(showClearConfirmation = false) }
        }
    }

    fun dismissClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    // Чат
    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val contactId = _uiState.value.activeContactId ?: return

        viewModelScope.launch {
            sendMessageUseCase(SendChatMessageParams(text, contactId))
            _uiState.update { it.copy(inputText = "") }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
```

### 7.3. Koin — MeshToChatAdapterModule (app/di/)

```kotlin
package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter

val meshToChatAdapterModule = module {
    single { MeshToChatAdapter(packetRepository = get(), nodeRepository = get()) }
}
```

### 7.4. Koin — ChatDomainModule.kt (новый)

```kotlin
package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.domain.chat.usecase.*

val chatDomainModule = module {
    single { ObserveChatContactsUseCase(get()) }
    single { ObserveChatMessagesUseCase(get()) }
    single { SendChatMessageUseCase(get()) }
    single { ToggleChatFavoriteContactUseCase(get()) }
    single { ToggleChatArchivedContactUseCase(get()) }
    single { ClearChatHistoryUseCase(get()) }
    single { MarkChatAsReadUseCase(get()) }
}
```

### 7.5. Koin — ChatDataModule.kt (новый)

```kotlin
package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.chat.repository.ChatRepositoryImpl
import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository

val chatDataModule = module {
    single<ChatRepository> { ChatRepositoryImpl(adapter = get()) }
}
```

### 7.6. Koin — PresentationModule.kt (обновить)

```kotlin
viewModel {
    ChatViewModel(
        observeContactsUseCase = get(),
        observeMessagesUseCase = get(),
        sendMessageUseCase = get(),
        toggleFavoriteUseCase = get(),
        toggleArchivedUseCase = get(),
        clearHistoryUseCase = get(),
        markAsReadUseCase = get(),
    )
}
```

---

## ФАЗА 8: Интеграция с mesh

> Mesh-модуль — **источник данных**. Chat-фича **НЕ импортирует** mesh-модели.
> Конвертация происходит ТОЛЬКО в `MeshToChatAdapter`.

### 8.1. Отправка сообщений
- `SendChatMessageUseCase` → `ChatRepository.sendMessage()` → `MeshToChatAdapter.sendMessage()` → mesh-слой

### 8.2. Получение сообщений
- `observeMessages(contactIds)` → `MeshToChatAdapter.observeMessagesAsFlow()` → `PacketRepository.getMessagesFrom()` → mapper → DTO → domain → Flow

### 8.3. Получение контактов
- `observeContacts()` → `MeshToChatAdapter.observeContactsAsFlow()` → `PacketRepository.getContacts()` + `getContactSettings()` → mapper → DTO → domain → Flow

---

## ФАЗА 9: Тестирование

### 9.1. Сценарии
- [ ] Сохранение состояния между сессиями (чекбоксы, избранное, закреплённые, архив)
- [ ] Отправка сообщения через mesh
- [ ] Получение сообщения через mesh
- [ ] Свайпы между вкладками
- [ ] Контекстное меню (закрепить, избранное, архив, прочитано, очистить)
- [ ] Поиск по сообщениям
- [ ] Клик на контакт → только его сообщения
- [ ] Чекбоксы → сообщения всех выбранных
- [ ] Клавиатура не перекрывает строку ввода

### 9.2. Build check
- [ ] `./gradlew :app:assembleDebug` — без ошибок
- [ ] `./gradlew :app:lint` — без критических предупреждений

---

## Файлы для создания/изменения

### Новые файлы:

**Domain-модели (app/domain/chat/model/):**
```
app/src/main/java/ru/tcynik/meshtactics/domain/chat/model/ChatContact.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/model/ChatMessage.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/model/ChatTab.kt
```

**Use Cases (app/domain/chat/usecase/):**
```
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ObserveChatContactsUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ObserveChatMessagesUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/SendChatMessageUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ToggleChatFavoriteContactUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ToggleChatArchivedContactUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ClearChatHistoryUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/MarkChatAsReadUseCase.kt
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/SearchChatMessagesUseCase.kt
```

**Params (app/domain/chat/usecase/):**
```
app/src/main/java/ru/tcynik/meshtactics/domain/chat/usecase/ChatUseCaseParams.kt
  (ObserveChatMessagesParams, SendChatMessageParams, ToggleFavoriteParams,
   ToggleArchivedParams, ClearHistoryParams, MarkAsReadParams, SearchMessagesParams)
```

**Repository интерфейс (app/domain/chat/repository/):**
```
app/src/main/java/ru/tcynik/meshtactics/domain/chat/repository/ChatRepository.kt
```

**DTO (app/data/chat/dto/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/dto/ChatContactDto.kt
app/src/main/java/ru/tcynik/meshtactics/data/chat/dto/ChatMessageDto.kt
```

**Adapter (app/data/chat/adapter/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/adapter/MeshToChatAdapter.kt
```

**Repository реализация (app/data/chat/repository/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/repository/ChatRepositoryImpl.kt
```

**DI модули (app/di/):**
```
app/src/main/java/ru/tcynik/meshtactics/di/MeshToChatAdapterModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/ChatDomainModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/ChatDataModule.kt
```

**UI компоненты (app/presentation/feature/chat/):**
```
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/filter/FilterTabContent.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/filter/FilterTopBar.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/filter/ContactList.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/filter/ContactListItem.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/filter/ContactDropdownMenu.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/chat/ChatTabContent.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/chat/ChatSearchBar.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/chat/MessageList.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/chat/MessageBubble.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/components/chat/ChatInputBar.kt
```

### Изменяемые файлы:
```
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatUiState.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatViewModel.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatScreen.kt
app/src/main/java/ru/tcynik/meshtactics/di/PresentationModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/MyMeshApplication.kt (или где определён startKoin — добавить модули)

mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/
├── database/entity/Packet.kt (ContactSettings: +isFavorite, +isPinned, +isArchived)
├── database/dao/PacketDao.kt (+ updateFavorite, updatePinned, updateArchived)
├── database/MeshtasticDatabase.kt (миграция)
├── repository/PacketRepository.kt (+ setFavorite, setPinned, setArchived)
└── data/repository/PacketRepositoryImpl.kt (реализация новых методов)
```

---

## Сводка ключевых архитектурных решений

| Решение | Обоснование |
|---------|-------------|
| Domain в `app/domain/chat/` | Каноническое место в проекте (10 существующих поддоменов) |
| Собственные DTO (`ChatContactDto`, `ChatMessageDto`) | Chat-фича НЕ зависит от mesh-моделей |
| `MeshToChatAdapter` — единственное место импорта mesh | Изоляция зависимости: только 1 файл в chat-фиче видит mesh-модели |
| `ChatRepositoryImpl` НЕ импортирует mesh | Работает только через adapter + DTO |
| Mapper = `dto.toDomain()` extension | Чистая конвертация DTO → domain |
| `isArchived` в модели и репозитории | Интерфейс готов, логика — заглушка в MVP |
| `SearchChatMessagesUseCase` — plain `invoke` | Синхронная операция, НЕ наследует `UseCase` |
| Чекбоксы в `UiState.selectedContactIds` | UI-состояние НЕ в domain-моделях |
| `togglePinned` в репозитории | Нужен для сортировки и UI |
