# Chat Feature Plan (revised against actual implementation)

> **Last updated**: 2026-04-15
> **Source of truth**: implemented code (presentation layer); plan updated to match it

---

## Architectural Context

- **UI**: Jetpack Compose + Material3
- **Navigation**: Navigation Compose (type-safe), `Route.Chat` already exists
- **DI**: Koin 4.x
- **Data**:
  - **Room** (`mesh/`) — mesh data: messages (Packet), contact settings (ContactSettings), nodes
  - **SQLDelight** (`shared/`) — KMP tactical nodes (NOT used for chat)
  - **DataStore** (`mesh/` or `app/data/`) — chat UI state persistence (active tab, selected contacts)
  - **Multiplatform Settings** (`shared/`) — simple key-value settings (not used for chat)
- **Existing messaging**: `Packet`, `PacketDao`, `ContactSettings`, `Message`, `DataPacket`, `MeshMessagingRepository`, `ObserveMessagesUseCase`, `SendMeshMessageUseCase` in the mesh layer

## Actual Dependency Graph

```
app/presentation/          ← depends on app/domain/
    ↓
app/domain/                ← pure Kotlin, does NOT depend on mesh/
    ↓
app/data/                  ← implements app/domain/ interfaces
    ↓
mesh/                      ← Meshtastic protocol (Room, BLE, packets)
```

## Mesh Model Isolation Rule

> The chat feature **NEVER imports** `ru.tcynik.meshtactics.mesh.model.*`.
> Conversion from mesh → chat happens **only** inside `MeshToChatAdapter` (`app/data/chat/adapter/`).

---

## Current Status (2026-04-15)

| Component | Status | File |
|---|---|---|
| `ChatMessageModel` | ✅ Done | `domain/chat/model/ChatMessageModel.kt` |
| `ChatContact`, `ContactType` | ✅ Done | `domain/chat/model/ChatContact.kt`, `ContactType.kt` |
| `ChatRepository` interface | ✅ Done | `domain/chat/repository/ChatRepository.kt` |
| 8 use cases + params | ✅ Done | `domain/chat/usecase/` |
| `ChatContactDto` + toDomain() | ✅ Done | `data/chat/dto/ChatContactDto.kt` |
| `MeshToChatAdapter` | ✅ Done | `data/chat/adapter/MeshToChatAdapter.kt` |
| `ChatRepositoryImpl` | ✅ Done | `data/chat/repository/ChatRepositoryImpl.kt` |
| `ChatPrefsRepository` (DataStore) | ✅ Done | `data/chat/prefs/ChatPrefsRepository.kt` |
| `ChatDataModule` (Koin) | ✅ Done | `di/ChatDataModule.kt` |
| Room `ContactSettings` + 3 new columns | ✅ Done | `mesh/.../entity/Packet.kt`, DB version 2 |
| `PacketDao` — updateFavorite/Pinned/Archived | ✅ Done | `mesh/.../dao/PacketDao.kt` |
| `PacketRepository` — setFavorite/Pinned/Archived | ✅ Done | `mesh/.../repository/PacketRepository.kt` |
| `ChatFilterItem` | ✅ Done | `presentation/feature/chat/model/ChatFilterItem.kt` |
| `ChatTab`, `ChatType` | ✅ Done | `presentation/feature/chat/model/ChatFilterItem.kt` |
| `ChatUiState` (with `allMessages`) | ✅ Done | `presentation/feature/chat/ChatUiState.kt` |
| `ChatViewModel` (real data, 8 use cases) | ✅ Done | `presentation/feature/chat/ChatViewModel.kt` |
| `ChatScreen` — Filter tab | ✅ Done | `presentation/feature/chat/ChatScreen.kt` |
| `ChatScreen` — Chat tab | ✅ Done | `presentation/feature/chat/ChatScreen.kt` |
| Archive UI (section + ArchiveItemRow) | ✅ Done | `presentation/feature/chat/ChatScreen.kt` |
| Archive logic (move / unarchive) | ✅ Done | `ChatViewModel.kt` |
| `onMoveFromArchive` wired up | ✅ Done | `ChatScreen.kt` — `FilterTabContent` param |
| Auto-read on chat open | ✅ Done | `ChatViewModel.selectChat()` calls `markAsRead()` |
| Unread badge counts archive children | ✅ Done | `collectUnreadAll()` in `ChatScreen.kt` |
| Unread badge on HUD chat button | ✅ Done | `MainViewModel` — `observeChatContacts` → `unreadChatCount` в `MainUiState` → `infoBadge` на кнопке чата |

---

## Design Decisions (deviations from the original plan)

### 1. `isChecked` is stored inside `ChatFilterItem` (not as `selectedContactIds` in UiState)

**Accepted in the implementation**: checkbox state lives directly in `ChatFilterItem.isChecked`.

**Consequence when connecting real data**: when the contacts Flow emits a new list, checkbox state will be lost unless preserved separately. Fix: when mapping `ChatContact → ChatFilterItem`, read `isChecked` from DataStore (or a `selectedContactIds` set in UiState), **not** from the domain model.

### 2. `ChatType` instead of `ContactType`

In code: `ChatType { CHANNEL, DIRECT_CHAT }` (in `presentation/feature/chat/model/`)
Original plan had: `ContactType { CHANNEL, PRIVATE }` (in `domain/chat/model/`)

**Decision**: keep `ChatType` in the presentation layer as-is. When the domain layer is implemented, introduce `ContactType { CHANNEL, PRIVATE }` there — mapping happens in the data layer.

### 3. Dynamic Chat tab title

The tab label is computed dynamically:
- Nothing selected → `"Feed"`
- One contact selected → `"Chat with {name}"`
- N contacts selected → `"Feed (N)"`

### 4. HorizontalPager instead of manual swipe gestures

`HorizontalPager` is used — swipe between tabs works natively. The manual gesture detection from the original plan is not needed.

### 5. `allMessages` must be a separate field (critical bug in current code)

> ⚠️ **Bug**: `updateFilteredMessages()` overwrites `state.messages` with the filtered result. On the next filter pass, the original full list is gone.

**Fix (next ViewModel refactor)**:
```kotlin
data class ChatUiState(
    val allMessages: ImmutableList<ChatMessageModel> = persistentListOf(),  // source of truth
    val messages: ImmutableList<ChatMessageModel> = persistentListOf(),     // derived / displayed
    ...
)
```

---

## Archive: Accepted Behaviour

**Approach: physical move within `filterItems`**

- Structure: regular items in a flat list + a single item with `isArchiveSection = true` that holds `children`
- **Archive**: the item is removed from the main list and appended to the archive section's `children`
- **Unarchive**: the item is removed from `children` and inserted back into the main list (sorted: `isPinned` first, then `lastMessageTime` descending)
- Archived items **have no checkbox** (`ArchiveItemRow` differs from `ChatFilterItemRow`)
- The context menu inside `ArchiveItemRow` has **"Unarchive"** instead of **"Archive"**

**When connecting real data**: the `isArchived` flag in `ContactSettings` (Room) determines initial placement.

---

## Auto-read Behaviour

When a user opens a chat (`selectChat(chatId)`), all messages of that contact are automatically marked as read:
1. `unreadCount` of that contact → 0 immediately in UI
2. Once the real data layer exists — calls `markAsRead(contactId)` → updates `ContactSettings` in Room

**Not triggered** on checkbox selection — only on item click.

---

## State Persistence Between Sessions

| State | Storage | When |
|---|---|---|
| Active tab (`currentTab`) | DataStore | Phase 3 |
| Selected chat (`selectedChatId`) | DataStore | Phase 3 |
| Checked contacts (`isChecked`) | DataStore (`Set<String>` of selected IDs) | Phase 3 |
| Favourite, pinned (`isFavorite`, `isPinned`) | Room (ContactSettings) | Phase 3 |
| Archived (`isArchived`) | Room (ContactSettings) | Phase 3 |
| Unread count (`unreadCount`) | Room (PacketDao) | Existing mechanism |

---

## Known Gaps / Deferred Work

| # | Problem | Location | Priority |
|---|---|---|---|
| 1 | ~~Unread count reactive — `unreadCount = 0` hardcoded in `MeshToChatAdapter`~~ | ✅ Fixed — `getUnreadCountFlow` подключён через `flatMapLatest` + `combine` | ✅ Done |
| 2 | DataStore state not restored on ViewModel `init` (tab, selectedChatId) | `ChatViewModel.kt` | 🟡 Medium — persistence saves but doesn't reload |
| 3 | `collectUnreadAll` duplicated in `ChatScreen` and `ChatViewModel` | — | 🟢 Low |

---

## PHASE 1: Domain Layer (app/domain/chat/) — not started

### 1.1. Models (app/domain/chat/model/)

Already done:
- `ChatMessageModel.kt` ✅ (`domain/chat/model/`)

To add:
- `ChatContact.kt` — contact (id, displayName, type: ContactType, isFavorite, isPinned, isArchived, unreadCount, lastMessageTime, lastMessagePreview)
- `ContactType.kt` — `enum { CHANNEL, PRIVATE }`

```kotlin
// domain/chat/model/ChatContact.kt
data class ChatContact(
    val id: String,
    val displayName: String,
    val type: ContactType,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageTime: Long? = null,
    val lastMessagePreview: String? = null,
)

enum class ContactType { CHANNEL, PRIVATE }
```

### 1.2. Repository Interface (app/domain/chat/repository/)

```kotlin
interface ChatRepository {
    fun observeContacts(): Flow<List<ChatContact>>
    fun observeMessages(contactIds: Set<String>, searchQuery: String = ""): Flow<List<ChatMessageModel>>
    suspend fun sendMessage(text: String, contactId: String, channel: Int)
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean)
    suspend fun togglePinned(contactId: String, isPinned: Boolean)
    suspend fun toggleArchived(contactId: String, isArchived: Boolean)
    suspend fun clearHistory(contactId: String)
    suspend fun markAsRead(contactId: String)
}
```

### 1.3. Use Cases (app/domain/chat/usecase/)

| Use Case | Type | Description |
|---|---|---|
| `ObserveChatContactsUseCase` | `FlowUseCase<NoParams, List<ChatContact>>` | Contact list flow |
| `ObserveChatMessagesUseCase` | `FlowUseCase<ObserveChatMessagesParams, List<ChatMessageModel>>` | Message flow |
| `SendChatMessageUseCase` | `UseCase<SendChatMessageParams, Unit>` | Send a message |
| `ToggleChatFavoriteUseCase` | `UseCase<ToggleFavoriteParams, Unit>` | Toggle favourite |
| `ToggleChatArchivedUseCase` | `UseCase<ToggleArchivedParams, Unit>` | Toggle archived |
| `ToggleChatPinnedUseCase` | `UseCase<TogglePinnedParams, Unit>` | Toggle pinned |
| `ClearChatHistoryUseCase` | `UseCase<ClearHistoryParams, Unit>` | Clear conversation history |
| `MarkChatAsReadUseCase` | `UseCase<MarkAsReadParams, Unit>` | Mark as read |

Params classes in `usecase/`:
```kotlin
data class ObserveChatMessagesParams(val contactIds: Set<String>, val searchQuery: String = "")
data class SendChatMessageParams(val text: String, val contactId: String, val channel: Int = 0)
data class ToggleFavoriteParams(val contactId: String, val isFavorite: Boolean)
data class ToggleArchivedParams(val contactId: String, val isArchived: Boolean)
data class TogglePinnedParams(val contactId: String, val isPinned: Boolean)
data class ClearHistoryParams(val contactId: String)
data class MarkAsReadParams(val contactId: String)
```

---

## PHASE 2: Data Layer (app/data/chat/) — not started

### 2.1. DTOs (app/data/chat/dto/)

```kotlin
// ChatContactDto.kt — does NOT import mesh models
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

### 2.2. MeshToChatAdapter (app/data/chat/adapter/)

> ⚠️ **The only place** in the chat feature that imports `mesh.model.*`

Converts:
- `DataPacket` + `ContactSettings` → `ChatContactDto`
- `Message` + `Node` → `ChatMessageModel`
- Delegates write operations to `PacketRepository`

### 2.3. ChatRepositoryImpl (app/data/chat/repository/)

```kotlin
class ChatRepositoryImpl(private val adapter: MeshToChatAdapter) : ChatRepository {
    override fun observeContacts() =
        adapter.observeContactsAsFlow().map { list -> list.map(ChatContactDto::toDomain) }
    override fun observeMessages(contactIds: Set<String>, searchQuery: String) =
        adapter.observeMessagesAsFlow(contactIds, searchQuery)
    // all write operations delegate to adapter
}
```

---

## PHASE 3: Persistence — not started

### 3.1. Room: extend ContactSettings (mesh/database/entity/Packet.kt)

Add columns:
```kotlin
@ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
@ColumnInfo(name = "is_pinned",   defaultValue = "0") val isPinned: Boolean = false,
@ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
```

Room migration: version +1, `ALTER TABLE contact_settings ADD COLUMN ...`

### 3.2. PacketDao — new queries

```kotlin
@Query("UPDATE contact_settings SET is_favorite = :v WHERE contact_key = :key")
suspend fun updateFavorite(key: String, v: Boolean)

@Query("UPDATE contact_settings SET is_pinned = :v WHERE contact_key = :key")
suspend fun updatePinned(key: String, v: Boolean)

@Query("UPDATE contact_settings SET is_archived = :v WHERE contact_key = :key")
suspend fun updateArchived(key: String, v: Boolean)

@Query("UPDATE contact_settings SET unread_messages = 0 WHERE contact_key = :key")
suspend fun clearUnread(key: String)
```

### 3.3. DataStore: UI state keys

```kotlin
object ChatPrefsKeys {
    val CURRENT_TAB     = stringPreferencesKey("chat_current_tab")    // "FILTER" | "CHAT"
    val SELECTED_CHAT_ID = stringPreferencesKey("chat_selected_id")   // nullable
    val CHECKED_IDS     = stringSetPreferencesKey("chat_checked_ids") // Set<String>
}
```

Implement `ChatPrefsRepository` or extend the existing preferences repository.

---

## PHASE 4: Complete Archive Logic — in progress

### 4.1. ViewModel changes

#### `moveToArchive(itemId: String)` — physical move into archive

```kotlin
fun moveToArchive(itemId: String) {
    _uiState.update { state ->
        val item = state.filterItems.find { it.id == itemId } ?: return@update state
        val mainItems = state.filterItems
            .filter { !it.isArchiveSection && it.id != itemId }
        val archiveSections = state.filterItems
            .map { section ->
                if (section.isArchiveSection)
                    section.copy(children = (section.children + item.copy(isChecked = false)).toImmutableList())
                else
                    section
            }
            .filter { it.isArchiveSection }
        state.copy(filterItems = (mainItems + archiveSections).toImmutableList())
    }
}
```

#### `moveFromArchive(itemId: String)` — restore from archive

```kotlin
fun moveFromArchive(itemId: String) {
    _uiState.update { state ->
        val archiveSection = state.filterItems.find { it.isArchiveSection } ?: return@update state
        val item = archiveSection.children.find { it.id == itemId } ?: return@update state
        val updatedArchive = archiveSection.copy(
            children = archiveSection.children.filter { it.id != itemId }.toImmutableList()
        )
        val mainItems = (state.filterItems.filter { !it.isArchiveSection } + item)
            .sortedWith(compareBy({ !it.isPinned }, { -it.lastMessageTime }))
        state.copy(filterItems = (mainItems + updatedArchive).toImmutableList())
    }
}
```

### 4.2. ChatScreen fix

In `FilterTabContent`, replace `TODO()`:
```kotlin
onMoveFromArchive = { viewModel.moveFromArchive(it) }
```

### 4.3. Fix `selectArchiveItems()`

Current bug: looks for archive IDs in the top-level `filterItems`, but archive children are nested inside `isArchiveSection` item.

Fix: operate directly on `archiveSection.children`.

---

## PHASE 5: Critical ViewModel Bug Fixes

### 5.1. Separate full and filtered message lists

Add to `ChatUiState`:
```kotlin
val allMessages: ImmutableList<ChatMessageModel> = persistentListOf(),  // source of truth
val messages: ImmutableList<ChatMessageModel> = persistentListOf(),     // displayed (filtered)
```

`updateFilteredMessages()` must filter from `allMessages`, not from `messages`.

### 5.2. Auto-read on chat open

Inside `selectChat(chatId)`:
```kotlin
fun selectChat(chatId: String) {
    // ... existing state update ...
    markAsRead(chatId)  // ← add this call
}
```

`markAsRead` sets `unreadCount = 0` on the corresponding `ChatFilterItem` immediately in UI.

### 5.3. Unread badge in TabRow

Replace `sumOf { it.unreadCount }` with the existing `collectUnreadAll()`:
```kotlin
val totalUnread = collectUnreadAll(uiState.filterItems)  // also counts archive children
```

---

## PHASE 6: Real Data Integration (after Phases 1–3)

### 6.1. ViewModel: inject use cases via Koin

```kotlin
class ChatViewModel(
    private val observeContactsUseCase: ObserveChatContactsUseCase,
    private val observeMessagesUseCase: ObserveChatMessagesUseCase,
    private val sendMessageUseCase: SendChatMessageUseCase,
    private val toggleFavoriteUseCase: ToggleChatFavoriteUseCase,
    private val toggleArchivedUseCase: ToggleChatArchivedUseCase,
    private val togglePinnedUseCase: ToggleChatPinnedUseCase,
    private val clearHistoryUseCase: ClearChatHistoryUseCase,
    private val markAsReadUseCase: MarkChatAsReadUseCase,
) : ViewModel()
```

Remove `loadFakeData()`. Contacts Flow → map `ChatContact → ChatFilterItem` using `isChecked` from DataStore.

### 6.2. ChatContact → ChatFilterItem mapping

Mapper in the ViewModel or a dedicated class:
```kotlin
fun ChatContact.toFilterItem(isChecked: Boolean): ChatFilterItem = ChatFilterItem(
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
// contacts with isArchived = true go into archive section children, not the main list
```

### 6.3. Koin modules

```kotlin
// ChatDomainModule
val chatDomainModule = module {
    single { ObserveChatContactsUseCase(get()) }
    single { ObserveChatMessagesUseCase(get()) }
    single { SendChatMessageUseCase(get()) }
    single { ToggleChatFavoriteUseCase(get()) }
    single { ToggleChatArchivedUseCase(get()) }
    single { ToggleChatPinnedUseCase(get()) }
    single { ClearChatHistoryUseCase(get()) }
    single { MarkChatAsReadUseCase(get()) }
}

// ChatDataModule
val chatDataModule = module {
    single { MeshToChatAdapter(packetRepository = get(), nodeRepository = get()) }
    single<ChatRepository> { ChatRepositoryImpl(adapter = get()) }
}

// PresentationModule — add:
viewModel { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
```

---

## Target File Structure

```
app/
├── domain/
│   └── chat/
│       ├── model/
│       │   ├── ChatMessageModel.kt            ✅ (relocated)
│       │   ├── ChatContact.kt                 ⬜
│       │   └── ContactType.kt                 ⬜
│       ├── repository/
│       │   └── ChatRepository.kt              ⬜
│       └── usecase/
│           ├── ObserveChatContactsUseCase.kt  ⬜
│           ├── ObserveChatMessagesUseCase.kt  ⬜
│           ├── SendChatMessageUseCase.kt       ⬜
│           └── ... (remaining use cases)      ⬜
├── data/
│   └── chat/
│       ├── adapter/
│       │   └── MeshToChatAdapter.kt           ⬜
│       ├── dto/
│       │   └── ChatContactDto.kt              ⬜
│       └── repository/
│           └── ChatRepositoryImpl.kt          ⬜
└── presentation/
    └── feature/
        └── chat/
            ├── model/
            │   └── ChatFilterItem.kt          ✅ (ChatType and ChatTab defined here)
            ├── ChatUiState.kt                 ✅ (needs allMessages field)
            ├── ChatViewModel.kt               ✅ (fake data; needs bug fixes)
            └── ChatScreen.kt                  ✅ (needs onMoveFromArchive wired up)
```
