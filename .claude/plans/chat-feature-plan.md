# Chat Feature Development Plan (Revised)

## Architectural Context
- **UI**: Jetpack Compose + Material3
- **Navigation**: Navigation Compose (type-safe), `Route.Chat` already exists
- **DI**: Koin 4.x
- **Data**:
  - **Room** (`mesh/`) — mesh data: messages (Packet), contact settings (ContactSettings), nodes, logs
  - **SQLDelight** (`shared/`) — tactical nodes for KMP map (1 table `Node`, NOT used for chats)
  - **DataStore** (`mesh/`) — mesh/UI settings, configs, protobuf
  - **Multiplatform Settings** (`shared/`) — simple settings (3 keys: device_id, marker_size_level)
- **Existing messaging**: `Packet` entity, `PacketDao`, `ContactSettings`, `Message`, `DataPacket`, `MeshMessagingRepository`, `ObserveMessagesUseCase`, `SendMeshMessageUseCase` in the mesh layer

## Existing Architecture (Important)

The project uses **`app/` as the presentation+domain+data layer**, not `shared/`:
- `app/domain/` — domain interfaces (repositories, use cases, models) — **this is the canonical location for domain in this project**
- `app/data/` — repository implementations, mappers, DTOs, adapters
- `shared/` — KMP layer with SQLDelight (tactical data only)
- `mesh/` — Meshtastic protocol (Room, BLE, packets, service) — uses **annotated Koin** (`@Single`, `@Module`)

**Dependency Law (actual in project):**
```
app/presentation/          ← depends on app/domain/
    ↓
app/domain/                ← pure Kotlin, does NOT depend on mesh/
    ↓
app/data/                  ← implements app/domain/ interfaces
    ↓
mesh/                      ← Meshtastic protocol (Room, BLE, packets)
```

## Key Rule: Chat feature does NOT import mesh models

> **The chat feature uses its OWN DTOs and its OWN domain models.**
> Conversion from mesh models to Chat DTOs happens in a **single** location — `MeshToChatAdapter` in `app/data/chat/adapter/`.
> Neither the domain layer, nor mappers, nor ViewModel, nor UI import `ru.tcynik.meshtactics.mesh.model.*`.

```
mesh models (DataPacket, Message, Contact)
    ↓
MeshToChatAdapter          ← ONLY location that imports mesh models
    ↓ converts mesh → DTO
Chat DTOs (ChatContactDto, ChatMessageDto)
    ↓
Mapper (dto.toDomain())
    ↓
Domain models (ChatContact, ChatMessage)
    ↓
ViewModel → UI
```

## User Clarifications
- Click on an item → shows messages **from that contact only**, checkboxes do not change
- "Select all" and "Favorites" — **check checkboxes**, "Archive" — **stub**
- Archive logic is **not implemented yet**, but the interface **must exist** (`toggleArchived`)
- Messages in the unified feed are **interleaved by time** (like a messenger)
- Styles: **messenger bubbles** (outgoing on the right, incoming on the left)
- Reactions: **none**
- Search: **within filtered** messages

---

## PHASE 1: Domain Layer (app/domain/chat/)

### 1.1. Domain Models (app/domain/chat/model/)

#### `ChatContact.kt` — contact model for chat
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

/**
 * Domain model for a contact in the "Filter" tab.
 * Completely independent of mesh.model.Contact and mesh.model.ContactSettings.
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
    CHANNEL,        // channels (e.g. "^all")
    PRIVATE,        // private conversations
}
```

> ⚠️ **`isArchived` is included** — the field exists in the model, but archive logic is a stub in MVP.
> ⚠️ **`ChatContactItem` is NOT created** — checkbox state lives in `UiState.selectedContactIds`.

#### `ChatMessage.kt` — message model for chat
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

/**
 * Domain model for a message in the "Chat" tab.
 * Completely independent of mesh.model.Message and mesh.model.DataPacket.
 */
data class ChatMessage(
    val id: Long,                      // unique ID
    val contactId: String,             // contactKey (which contact it belongs to)
    val senderName: String,            // sender name
    val text: String,                  // message text
    val timestamp: Long,               // time in millis
    val isOutgoing: Boolean,           // true = outgoing
    val isRead: Boolean = false,       // whether it has been read
    val deliveryStatus: ChatMessageDelivery = ChatMessageDelivery.Pending,
)

enum class ChatMessageDelivery {
    Pending,    // awaiting send
    Sent,       // sent to radio
    Delivered,  // delivered
    Failed,     // error
}
```

#### `ChatTab.kt`
```kotlin
package ru.tcynik.meshtactics.domain.chat.model

enum class ChatTab(val label: String) {
    FILTER("Filter"),
    CHAT("Chat"),
}
```

### 1.2. Params for Use Cases (app/domain/chat/usecase/)

```kotlin
package ru.tcynik.meshtactics.domain.chat.usecase

/** Params: Set<ContactId> to display messages for selected contacts */
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

| Use Case | Base Class | Description |
|----------|------------|-------------|
| `ObserveChatContactsUseCase` | `FlowUseCase<NoParams, List<ChatContact>>` | Flow of contact list |
| `ObserveChatMessagesUseCase` | `FlowUseCase<ObserveChatMessagesParams, List<ChatMessage>>` | Flow of messages by contactId(s) |
| `SendChatMessageUseCase` | `UseCase<SendChatMessageParams, Unit>` | Send a message |
| `ToggleChatFavoriteContactUseCase` | `UseCase<ToggleFavoriteParams, Unit>` | Toggle favorite status |
| `ToggleChatArchivedContactUseCase` | `UseCase<ToggleArchivedParams, Unit>` | Toggle archived status |
| `ClearChatHistoryUseCase` | `UseCase<ClearHistoryParams, Unit>` | Clear conversation history |
| `MarkChatAsReadUseCase` | `UseCase<MarkAsReadParams, Unit>` | Mark as read |
| `SearchChatMessagesUseCase` | plain `operator fun invoke` | Synchronous search (NOT extends UseCase) |

> ⚠️ **`SearchChatMessagesUseCase`** — synchronous operation, therefore does NOT extend `UseCase` (per the rule: "Do NOT use `UseCase` for synchronous operations — use plain `operator fun invoke`").

### 1.4. Repository Interface (app/domain/chat/repository/ChatRepository.kt)

```kotlin
package ru.tcynik.meshtactics.domain.chat.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ChatMessage

/**
 * Repository for the Chat feature.
 * Works ONLY with domain models. Does NOT import mesh models.
 * Data source is MeshToChatAdapter in the data layer.
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

## PHASE 2: Data Layer (app/data/chat/)

### 2.1. DTOs (app/data/chat/dto/)

> ⚠️ **Own DTOs** — do NOT import mesh models. Pure data classes.

#### `ChatContactDto.kt`
```kotlin
package ru.tcynik.meshtactics.data.chat.dto

import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ContactType

/**
 * Contact DTO. Pure data class, independent of mesh models.
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
 * Message DTO. Pure data class, independent of mesh models.
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

> ⚠️ **The ONLY place in the chat feature that imports mesh models.**
> Converts mesh models → Chat DTOs.

#### `MeshToChatAdapter.kt`
```kotlin
package ru.tcynik.meshtactics.data.chat.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.dto.ChatContactDto
import ru.tcynik.meshtactics.data.chat.dto.ChatMessageDto
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
 * Adapter: converts mesh models → Chat DTOs.
 * This is the ONLY place in the chat feature that imports mesh models.
 * All other chat feature components work exclusively with DTOs and domain models.
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
        // Delegates to mesh layer via PacketRepository
        // Implementation depends on how mesh sends messages
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
        // Synchronous search — implement when needed
        return emptyList()
    }
}

// ---- Extension functions: mesh → DTO conversion ----

private fun DataPacket.toContact(): Contact {
    // Convert DataPacket → Contact (extract fields from dataPacket)
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
```

### 2.3. Repository Implementation (app/data/chat/repository/ChatRepositoryImpl.kt)

> ⚠️ **Does NOT import mesh models.** Works only with `MeshToChatAdapter` and DTOs.

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
 * ChatRepositoryImpl does NOT import mesh models.
 * All conversion from mesh happens in MeshToChatAdapter.
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

## PHASE 3: State Persistence (Room in mesh module)

> ⚠️ **SQLDelight is NOT used for chats**. It is reserved for KMP-layer tactical data.
> All chat data is stored in **Room** (`mesh/` module).

### 3.1. Extend ContactSettings (mesh/database/entity/Packet.kt)

In `ContactSettings` entity, add:
```kotlin
@ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
@ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
@ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
```

### 3.2. Database Migration
- Version: current → +1
- `ALTER TABLE contact_settings ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE contact_settings ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0`
- `ALTER TABLE contact_settings ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0`
- During development: `fallbackToDestructiveMigration()` until migration is tested

### 3.3. PacketDao — New Methods

```kotlin
@Query("UPDATE contact_settings SET is_favorite = :isFavorite WHERE contact_key = :contactKey")
suspend fun updateFavorite(contactKey: String, isFavorite: Boolean)

@Query("UPDATE contact_settings SET is_pinned = :isPinned WHERE contact_key = :contactKey")
suspend fun updatePinned(contactKey: String, isPinned: Boolean)

@Query("UPDATE contact_settings SET is_archived = :isArchived WHERE contact_key = :contactKey")
suspend fun updateArchived(contactKey: String, isArchived: Boolean)

// Sorting: pinned → normal by time
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

### 3.4. PacketRepository (mesh interface) — New Methods

In `mesh/repository/PacketRepository.kt`:
```kotlin
suspend fun setFavorite(contactKey: String, isFavorite: Boolean)
suspend fun setPinned(contactKey: String, isPinned: Boolean)
suspend fun setArchived(contactKey: String, isArchived: Boolean)
```

In `mesh/data/repository/PacketRepositoryImpl.kt` — delegate to DAO.

---

## PHASE 4: UI "Filter" Tab

### 4.1. Component Structure (app/presentation/feature/chat/components/filter/)

```
FilterTabContent.kt          — main tab screen
FilterTopBar.kt              — buttons: Select All, Favorites, Archive (stub)
ContactList.kt               — LazyColumn of contacts
ContactListItem.kt           — single list item
ContactDropdownMenu.kt       — overflow menu (⋮)
```

### 4.2. ContactListItem
```
┌────────────────────────────────────────────────────┐
│ ☑  ★  Channel Name        12   14:30         ⋮     │
└────────────────────────────────────────────────────┘
```
- **Checkbox** (left) — checkbox state (from `UiState.selectedContactIds`)
- **Star** (favorite) — visible if `contact.isFavorite = true`
- **Archived badge** — if `contact.isArchived = true` (stub, visual only)
- **Name** — `contact.displayName`
- **Unread badge** — if `contact.unreadCount > 0`
- **Time** — formatted `contact.lastMessageTime`
- **DropdownMenu** (⋮):
  - Pin / Unpin
  - Add to Favorites / Remove from Favorites
  - Mark as Read
  - Archive / Unarchive (toggles `isArchived` via `toggleArchived`)
  - Clear (with confirmation dialog)

### 4.3. FilterTopBar
```
[ Select All ]  [ Favorites ]  [ Archive ]
```
- "Select All" — checks all checkboxes
- "Favorites" — checks only favorite contacts
- "Archive" — stub (Snackbar "Coming soon")

### 4.4. Sorting Logic
- Pinned contacts at the top
- Remaining sorted by `lastMessageTime` (newest first)

---

## PHASE 5: UI "Chat" Tab

### 5.1. Component Structure (app/presentation/feature/chat/components/chat/)

```
ChatTabContent.kt            — Scaffold for the tab
ChatSearchBar.kt             — TopAppBar with search
MessageList.kt               — LazyColumn of messages
MessageBubble.kt             — message bubble (incoming/outgoing)
ChatInputBar.kt              — BottomAppBar (TextField + send button)
```

### 5.2. Layout
```
┌─────────────────────────────────────────┐
│  [Search messages...]                   │ ← ChatSearchBar
├─────────────────────────────────────────┤
│                                         │
│  ╭────────────╮                         │ ← Incoming (left)
│  │ Hello!     │                         │
│  ╰────────────╯                                  │
│         ╭────────────╮                   │
│         │   Hello!   │                   │ ← Outgoing (right)
│         ╰────────────╯                   │
│                                         │
├─────────────────────────────────────────┤
│  [Type a message...]           [➤]      │ ← ChatInputBar
└─────────────────────────────────────────┘
```

### 5.3. MessageBubble
- **Incoming** (`isOutgoing = false`): aligned left, background color `surfaceVariant`
- **Outgoing** (`isOutgoing = true`): aligned right, background color `primaryContainer`
- Text + timestamp inside the bubble
- Sorted by time (oldest ↑)
- `LazyColumn(reverseLayout = false)` with initial scroll to bottom

### 5.4. ChatInputBar
- `TextField` + `IconButton` (send)
- `Modifier.imePadding()` for keyboard handling
- When keyboard appears — input bar rises above

### 5.5. Logic
- Click on contact → `activeContactId` → only that contact's messages
- With checkboxes → messages from all selected contacts in unified feed
- Search filters by text (within filtered contacts only)

---

## PHASE 6: Navigation & Swipes

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

### 6.2. Swipes
- `Modifier.pointerInput` + `detectHorizontalDragGestures`
- Swipe left on "Filter" → switch to "Chat"
- Swipe right on "Chat" → switch to "Filter"
- Swipe right on "Filter" → `onNavigateBack()` (main screen)

---

## PHASE 7: ViewModel + UiState + DI

### 7.1. ChatUiState (Revised)

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
 * ✅ Checkboxes are stored in UiState (selectedContactIds),
 * NOT in a domain model like ChatContactItem.
 */
data class ChatUiState(
    val activeTab: ChatTab = ChatTab.FILTER,
    val contacts: ImmutableList<ChatContact> = persistentListOf(),
    val messages: ImmutableList<ChatMessage> = persistentListOf(),
    val selectedContactIds: ImmutableSet<String> = persistentSetOf(),
    val activeContactId: String? = null,   // when clicking on a contact
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

    // ── Navigation ──

    fun onTabSelected(tab: ChatTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onNavigateBack() { /* callback via NavGraph */ }

    // ── Filter Tab ──

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

    // ── Chat Tab ──

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

### 7.4. Koin — ChatDomainModule.kt (New)

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

### 7.5. Koin — ChatDataModule.kt (New)

```kotlin
package ru.tcynik.meshtactics.di

import org.koin.dsl.module
import ru.tcynik.meshtactics.data.chat.repository.ChatRepositoryImpl
import ru.tcynik.meshtactics.domain.chat.repository.ChatRepository

val chatDataModule = module {
    single<ChatRepository> { ChatRepositoryImpl(adapter = get()) }
}
```

### 7.6. Koin — PresentationModule.kt (Update)

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

## PHASE 8: Mesh Integration

> Mesh module — **data source**. Chat feature does **NOT import** mesh models.
> Conversion happens **ONLY** in `MeshToChatAdapter`.

### 8.1. Sending Messages
- `SendChatMessageUseCase` → `ChatRepository.sendMessage()` → `MeshToChatAdapter.sendMessage()` → mesh layer

### 8.2. Receiving Messages
- `observeMessages(contactIds)` → `MeshToChatAdapter.observeMessagesAsFlow()` → `PacketRepository.getMessagesFrom()` → mapper → DTO → domain → Flow

### 8.3. Receiving Contacts
- `observeContacts()` → `MeshToChatAdapter.observeContactsAsFlow()` → `PacketRepository.getContacts()` + `getContactSettings()` → mapper → DTO → domain → Flow

---

## PHASE 9: Testing

### 9.1. Scenarios
- [ ] State persistence between sessions (checkboxes, favorites, pinned, archived)
- [ ] Sending a message through mesh
- [ ] Receiving a message through mesh
- [ ] Swiping between tabs
- [ ] Context menu (pin, favorite, archive, mark as read, clear)
- [ ] Searching messages
- [ ] Click on contact → only that contact's messages
- [ ] Checkboxes → messages from all selected contacts
- [ ] Keyboard does not overlap the input bar

### 9.2. Build Check
- [ ] `./gradlew :app:assembleDebug` — no errors
- [ ] `./gradlew :app:lint` — no critical warnings

---

## Files to Create / Modify

### New Files:

**Domain models (app/domain/chat/model/):**
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

**Repository interface (app/domain/chat/repository/):**
```
app/src/main/java/ru/tcynik/meshtactics/domain/chat/repository/ChatRepository.kt
```

**DTOs (app/data/chat/dto/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/dto/ChatContactDto.kt
app/src/main/java/ru/tcynik/meshtactics/data/chat/dto/ChatMessageDto.kt
```

**Adapter (app/data/chat/adapter/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/adapter/MeshToChatAdapter.kt
```

**Repository implementation (app/data/chat/repository/):**
```
app/src/main/java/ru/tcynik/meshtactics/data/chat/repository/ChatRepositoryImpl.kt
```

**DI modules (app/di/):**
```
app/src/main/java/ru/tcynik/meshtactics/di/MeshToChatAdapterModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/ChatDomainModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/ChatDataModule.kt
```

**UI components (app/presentation/feature/chat/):**
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

### Modified Files:
```
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatUiState.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatViewModel.kt
app/src/main/java/ru/tcynik/meshtactics/presentation/feature/chat/ChatScreen.kt
app/src/main/java/ru/tcynik/meshtactics/di/PresentationModule.kt
app/src/main/java/ru/tcynik/meshtactics/di/MyMeshApplication.kt (or wherever startKoin is defined — add modules)

mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/
├── database/entity/Packet.kt (ContactSettings: +isFavorite, +isPinned, +isArchived)
├── database/dao/PacketDao.kt (+ updateFavorite, updatePinned, updateArchived)
├── database/MeshtasticDatabase.kt (migration)
├── repository/PacketRepository.kt (+ setFavorite, setPinned, setArchived)
└── data/repository/PacketRepositoryImpl.kt (implementation of new methods)
```

---

## Summary of Key Architectural Decisions

| Decision | Rationale |
|---------|-----------|
| Domain in `app/domain/chat/` | Canonical location in this project (10 existing subdomains) |
| Own DTOs (`ChatContactDto`, `ChatMessageDto`) | Chat feature does NOT depend on mesh models |
| `MeshToChatAdapter` — sole mesh import location | Dependency isolation: only 1 file in chat feature sees mesh models |
| `ChatRepositoryImpl` does NOT import mesh | Works exclusively through adapter + DTOs |
| Mapper = `dto.toDomain()` extension | Pure DTO → domain conversion |
| `isArchived` in model and repository | Interface ready, logic is a stub in MVP |
| `SearchChatMessagesUseCase` — plain `invoke` | Synchronous operation, does NOT extend `UseCase` |
| Checkboxes in `UiState.selectedContactIds` | UI state is NOT in domain models |
| `togglePinned` in repository | Required for sorting and UI |
