# Plan: Logical Channel Identity Refactor

**Date**: 2026-04-23
**Status**: Approved (Phase 1 complete)

## Summary

Replace `resolvedSlot` (stored in DB) as the routing key for message/geo-mark history with a
stable `channelHash = SHA-256(name + psk)`. Currently, if a channel has `resolvedSlot = null`
(never synced to the node), incoming packets on the matching slot are dropped — history lost.
After this refactor, slot→channel mapping is resolved at runtime from live node state via
`ChannelSlotResolver`, and all user data is keyed by `channelHash` — stable across node
reconnects, slot reassignments, and offline usage.

## Open Questions — Resolved

| Question | Decision |
|---|---|
| UI for unresolved incoming packets? | Drop + log warning. No "unknown channel" UI in this scope. |
| `channelHash` as primary key? | UUID stays as PK. Add `channel_hash TEXT NOT NULL UNIQUE` as secondary. |
| Migration of existing data? | `4.sqm` adds column as nullable. Repository computes and backfills on first load for rows where `channel_hash IS NULL`. |

## Scope

**In scope:**
- `LogicalChannelHash` value class + `computeChannelHash(name, psk)` utility function
- `ChannelSlotResolver` — domain interface + data implementation (runtime `Map<Int, LogicalChannelHash>` from live node)
- DB migration `4.sqm`: add `channel_hash TEXT` to `logical_channel`
- Remove `resolvedSlot` as a **persisted** field from `MeshtasticBinding`
- Update all 8 production files that use `resolvedSlot` for routing or storage
- `chat_message` and `geo_mark` — **no schema change** (keep `logical_channel_id` UUID, resolution path changes)

**Out of scope:**
- "Unknown channel" UI for unresolved incoming packets
- Dropping deprecated `meshtastic_slot` column (SQLite on API 24 has no DROP COLUMN; deferred)
- QR / deep-link channel import/export

## Architecture Notes

### chat_message / geo_mark stay UUID-keyed
These tables already store `logical_channel_id` (UUID). Resolution path after refactor:

```
incoming slot  →  ChannelSlotResolver.slotToHash[slot]  →  LogicalChannelHash
LogicalChannelHash  →  repo.findByHash(hash)  →  LogicalChannelId (UUID)
UUID  →  stored in chat_message / geo_mark
```

Only the lookup mechanism changes, not the storage schema.

### resolvedSlot removed from MeshtasticBinding entirely
No longer a persisted field on the domain model. Slot is ephemeral — derived at runtime from
`observeNodeChannels()` only. `UserSettingsViewModel` and `NodeProvisioningUseCase` get the
slot from `ChannelSlotResolver`, not from the stored binding.

### ChannelSlotResolver — three members
```kotlin
interface ChannelSlotResolver {
    val slotToHash: Map<Int, LogicalChannelHash>     // ingestion: slot → hash
    val hashToSlot: Map<LogicalChannelHash, Int>     // send: hash → slot
    val mapsFlow: StateFlow<ChannelSlotMaps>         // reactive combine for use cases
}
```
Built from `observeNodeChannels()` in `ChannelSlotResolverImpl` init block.
Injected into adapters, use cases, ViewModel. Domain interface — no data imports in domain.

### Unresolved incoming packets
Packets on slots absent from `slotToHash` → dropped, `Log.w(...)`. No data loss for known channels.

### Ingestion resolution path (2-step)
```
incoming packet.channel (Int)
  → maps.slotToHash[slot]           → LogicalChannelHash (or drop if null)
  → channelByHash[hash]             → LogicalChannelId   (built from observeChannels() combine)
  → stored in chat_message/geo_mark
```
`channelByHash` is built in-memory inside the combine block — no extra DB query needed for ingestion.
`findByChannelHash` (new repo method) is used only in `GeoMarkRepositoryImpl.resolveChannelId()`.

### NodeProvisioningUseCase — slot source after refactor
Provisions only channels with `SlotResolution.AlreadySynced`. Gets `nodeChannels` via
`observeNodeChannels(NoParams).first()` at provision time. No stored slot needed.

### pushChannelToNode — no longer saves to DB
After write, `channelSetFlow` updates → `ChannelSlotResolverImpl.init` re-emits maps automatically.
`saveLogicalChannel` call removed from `pushChannelToNode`.

### Pre-existing violations (out of scope)
- `IngestReceivedChatMessagesUseCase` (domain) imports `MeshToChatAdapter` (data)
- `IngestReceivedGeoMarksUseCase` (domain) imports `GeoMarkWaypointAdapter` (data)
- `NodeProvisioningUseCase` uses `android.util.Base64` (Android type in use case)

Fix separately; do not address in this refactor.

---

---

## Architecture Scaffolding (Phase 1 output)

### New: `domain/channel/model/LogicalChannelHash.kt`

```kotlin
package ru.tcynik.meshtactics.domain.channel.model

import java.security.MessageDigest

@JvmInline
value class LogicalChannelHash(val value: String) {
    companion object {
        fun compute(name: String, psk: ByteArray): LogicalChannelHash {
            val input = name.lowercase().toByteArray() + ":".toByteArray() + psk
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            return LogicalChannelHash(hex)
        }
    }
}
```

### New: `domain/channel/model/ChannelSlotMaps.kt`

```kotlin
package ru.tcynik.meshtactics.domain.channel.model

data class ChannelSlotMaps(
    val slotToHash: Map<Int, LogicalChannelHash> = emptyMap(),
    val hashToSlot: Map<LogicalChannelHash, Int> = emptyMap(),
)
```

### New: `domain/channel/ChannelSlotResolver.kt`

```kotlin
package ru.tcynik.meshtactics.domain.channel

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash

interface ChannelSlotResolver {
    val slotToHash: Map<Int, LogicalChannelHash>
    val hashToSlot: Map<LogicalChannelHash, Int>
    val mapsFlow: StateFlow<ChannelSlotMaps>
}
```

### Updated: `domain/channel/model/MeshtasticBinding.kt`

```kotlin
package ru.tcynik.meshtactics.domain.channel.model

data class MeshtasticBinding(
    val psk: ByteArray,
    val channelHash: LogicalChannelHash,
) : TransportBinding {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshtasticBinding) return false
        return psk.contentEquals(other.psk)
    }

    override fun hashCode(): Int = psk.contentHashCode()
}
```

### Updated: `domain/channel/repository/LogicalChannelRepository.kt`

```kotlin
interface LogicalChannelRepository {
    fun observeChannels(): Flow<List<LogicalChannel>>
    suspend fun saveChannel(channel: LogicalChannel)
    suspend fun deleteChannel(id: LogicalChannelId)
    suspend fun findByChannelHash(hash: LogicalChannelHash): LogicalChannel?   // NEW
}
```

### New: `shared/.../4.sqm`

```sql
ALTER TABLE logical_channel ADD COLUMN channel_hash TEXT;
```

### Updated: `shared/.../LogicalChannel.sq`

```sql
CREATE TABLE logical_channel (
    id              TEXT    NOT NULL PRIMARY KEY,
    name            TEXT    NOT NULL,
    meshtastic_slot INTEGER,           -- deprecated, no longer written; remove when minSdk ≥ 35
    meshtastic_psk  BLOB,
    is_auto_sync    INTEGER NOT NULL DEFAULT 0,
    channel_hash    TEXT
);

selectAll:
SELECT * FROM logical_channel ORDER BY rowid ASC;

selectByChannelHash:
SELECT * FROM logical_channel WHERE channel_hash = :channelHash LIMIT 1;

upsert:
INSERT OR REPLACE INTO logical_channel(id, name, meshtastic_slot, meshtastic_psk, is_auto_sync, channel_hash)
VALUES (:id, :name, :meshtasticSlot, :meshtasticPsk, :isAutoSync, :channelHash);

updateChannelHash:
UPDATE logical_channel SET channel_hash = :channelHash WHERE id = :id;

deleteById:
DELETE FROM logical_channel WHERE id = :id;
```

### Updated: `data/channel/repository/LogicalChannelRepositoryImpl.kt`

```kotlin
class LogicalChannelRepositoryImpl(
    private val queries: LogicalChannelQueries,
) : LogicalChannelRepository {

    override fun observeChannels(): Flow<List<LogicalChannel>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain(queries) } }

    override suspend fun saveChannel(channel: LogicalChannel) {
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
        val hash = if (binding != null) {
            LogicalChannelHash.compute(channel.metadata.name, binding.psk).value
        } else null
        queries.upsert(
            id = channel.id.value,
            name = channel.metadata.name,
            meshtasticSlot = null,      // no longer stored
            meshtasticPsk = binding?.psk,
            isAutoSync = if (channel.isAutoSync) 1L else 0L,
            channelHash = hash,
        )
    }

    override suspend fun deleteChannel(id: LogicalChannelId) {
        queries.deleteById(id.value)
    }

    override suspend fun findByChannelHash(hash: LogicalChannelHash): LogicalChannel? =
        queries.selectByChannelHash(hash.value)
            .executeAsOneOrNull()
            ?.toDomain(queries)
}

private fun Logical_channel.toDomain(queries: LogicalChannelQueries): LogicalChannel {
    val psk = meshtastic_psk ?: return LogicalChannel(
        id = LogicalChannelId(id),
        metadata = ChannelMetadata(name = name),
        transports = emptyList(),
        isAutoSync = is_auto_sync != 0L,
    )
    val hash = channel_hash?.let { LogicalChannelHash(it) }
        ?: LogicalChannelHash.compute(name, psk).also { computed ->
            // backfill: rows that predate migration 4 have channel_hash = null
            queries.updateChannelHash(channelHash = computed.value, id = id)
        }
    return LogicalChannel(
        id = LogicalChannelId(id),
        metadata = ChannelMetadata(name = name),
        transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
        isAutoSync = is_auto_sync != 0L,
    )
}
```

### Updated: `data/channel/repository/FakeLogicalChannelRepository.kt`

Add `findByChannelHash`:
```kotlin
override suspend fun findByChannelHash(hash: LogicalChannelHash): LogicalChannel? =
    _channels.value.firstOrNull { ch ->
        ch.transports.filterIsInstance<MeshtasticBinding>()
            .any { it.channelHash == hash }
    }
```

### New: `data/channel/ChannelSlotResolverImpl.kt`

```kotlin
package ru.tcynik.meshtactics.data.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ChannelSlotResolverImpl(
    observeNodeChannels: ObserveNodeChannelsUseCase,
) : ChannelSlotResolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _mapsFlow = MutableStateFlow(ChannelSlotMaps())
    override val mapsFlow: StateFlow<ChannelSlotMaps> = _mapsFlow.asStateFlow()

    override val slotToHash: Map<Int, LogicalChannelHash> get() = _mapsFlow.value.slotToHash
    override val hashToSlot: Map<LogicalChannelHash, Int> get() = _mapsFlow.value.hashToSlot

    init {
        observeNodeChannels(NoParams)
            .onEach { slots ->
                val slotToHash = slots
                    .filter { it.isEnabled }
                    .associate { slot -> slot.index to LogicalChannelHash.compute(slot.name, slot.psk) }
                _mapsFlow.value = ChannelSlotMaps(
                    slotToHash = slotToHash,
                    hashToSlot = slotToHash.entries.associate { (k, v) -> v to k },
                )
            }
            .launchIn(scope)
    }
}
```

### Updated: `domain/chat/usecase/IngestReceivedChatMessagesUseCase.kt`

```kotlin
class IngestReceivedChatMessagesUseCase(
    private val adapter: MeshToChatAdapter,
    private val channelRepository: LogicalChannelRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val channelSlotResolver: ChannelSlotResolver,      // NEW
) {
    fun observe(): Flow<Unit> = combine(
        adapter.observeMessagesAsFlow(emptySet(), ""),
        channelRepository.observeChannels(),
        channelSlotResolver.mapsFlow,                          // NEW — replaces resolvedSlot map
    ) { messages, channels, maps ->
        val channelByHash = channels
            .flatMap { ch ->
                ch.transports.filterIsInstance<MeshtasticBinding>()
                    .map { b -> b.channelHash to ch.id }
            }.toMap()

        messages.forEach { msg ->
            val contactKey = msg.channelId
            val nodeId = contactKey.dropWhile { it.isDigit() }
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: return@forEach
            val isChannel = nodeId.startsWith("^")

            val logicalChannelId = if (isChannel) {
                val hash = maps.slotToHash[channelIndex] ?: return@forEach  // drop if unresolved
                channelByHash[hash]?.value ?: return@forEach
            } else {
                contactKey
            }

            chatMessageRepository.insertIfAbsent(
                id = msg.id,
                logicalChannelId = logicalChannelId,
                senderNodeId = msg.senderNodeId,
                senderCallsign = msg.senderCallsign,
                text = msg.text,
                sentAt = msg.sentAt / 1_000L,
                isSelf = msg.isFromMe,
            )
        }
    }.map { }
}
```

### Updated: `domain/marker/usecase/IngestReceivedGeoMarksUseCase.kt`

```kotlin
class IngestReceivedGeoMarksUseCase(
    private val packetRepository: PacketRepository,
    private val channelRepository: LogicalChannelRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val channelSlotResolver: ChannelSlotResolver,      // NEW
) {
    fun observe(): Flow<Unit> = combine(
        packetRepository.getWaypoints(),
        channelRepository.observeChannels(),
        channelSlotResolver.mapsFlow,                          // NEW
    ) { packets, channels, maps ->
        val channelByHash = channels
            .flatMap { ch ->
                ch.transports.filterIsInstance<MeshtasticBinding>()
                    .map { b -> b.channelHash to ch.id }
            }.toMap()

        val nowSeconds = System.currentTimeMillis() / 1_000

        packets.forEach { packet ->
            val hash = maps.slotToHash[packet.channel] ?: return@forEach
            val logicalChannelId = channelByHash[hash] ?: return@forEach
            val model = adapter.decode(packet) ?: return@forEach
            val expiresAt = model.expiresAt
            if (expiresAt != null && expiresAt < nowSeconds) return@forEach
            geoMarkRepository.persistReceived(model, logicalChannelId)
        }
    }.map { }
}
```

### Updated: `data/chat/adapter/MeshToChatAdapter.kt` — diff only

Constructor: add `private val channelSlotResolver: ChannelSlotResolver`

`observeContactsAsFlow` — replace `channelByIndex` build:
```kotlin
// REMOVE:
val channelByIndex: Map<Int, LogicalChannelId> = channels
    .flatMap { ch ->
        ch.transports.filterIsInstance<MeshtasticBinding>()
            .mapNotNull { b -> b.resolvedSlot?.let { slot -> slot to ch.id } }
    }.toMap()

// ADD:
val channelByHash = channels
    .flatMap { ch ->
        ch.transports.filterIsInstance<MeshtasticBinding>()
            .map { b -> b.channelHash to ch.id }
    }.toMap()
val slotMaps = channelSlotResolver.mapsFlow.value   // snapshot inside flatMapLatest
```

Channel lookup inside `mapIndexed`:
```kotlin
// REMOVE:
val logicalChannelId = channelByIndex[channelIndex] ?: return@mapIndexed null

// ADD:
val hash = slotMaps.slotToHash[channelIndex] ?: return@mapIndexed null
val logicalChannelId = channelByHash[hash] ?: return@mapIndexed null
```

`sendMessage` — replace slot resolution:
```kotlin
// REMOVE: val channelIndex = binding.resolvedSlot ?: return
// ADD:
val channelIndex = channelSlotResolver.hashToSlot[binding.channelHash] ?: return
```

`resolveContactKey` — replace slot resolution:
```kotlin
// REMOVE: val slot = binding.resolvedSlot ?: return null
// ADD:
val slot = channelSlotResolver.hashToSlot[binding.channelHash] ?: return null
```

### Updated: `data/marker/repository/GeoMarkRepositoryImpl.kt` — diff only

Constructor: add `private val channelSlotResolver: ChannelSlotResolver`

`resolveChannelId`:
```kotlin
// BEFORE:
private suspend fun resolveChannelId(channelIndex: Int): String {
    return channelRepository.observeChannels().first()
        .firstOrNull { ch ->
            ch.transports.filterIsInstance<MeshtasticBinding>()
                .any { it.resolvedSlot == channelIndex }
        }?.id?.value ?: ""
}

// AFTER:
private suspend fun resolveChannelId(channelIndex: Int): String {
    val hash = channelSlotResolver.slotToHash[channelIndex] ?: return ""
    return channelRepository.findByChannelHash(hash)?.id?.value ?: ""
}
```

### Updated: `domain/mesh/usecase/NodeProvisioningUseCase.kt`

```kotlin
class NodeProvisioningUseCase(
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,   // NEW
    private val resolveSlot: ResolveChannelSlotUseCase,            // NEW
) {
    suspend fun provision() {
        val channels = observeLogicalChannels(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val user = observeAppUser(NoParams).first()

        channels.forEach { channel ->
            val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
                ?: return@forEach
            val slot = when (val r = resolveSlot(channel, nodeChannels)) {
                is SlotResolution.AlreadySynced -> r.slot
                else -> return@forEach  // provision only what's already on node
            }
            val pskBase64 = Base64.encodeToString(binding.psk, Base64.NO_WRAP)
            writeChannel(slot, channel.metadata.name, pskBase64)
        }

        if (user.displayName.isNotBlank()) {
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }
    }
}
```

### Updated: `presentation/feature/settings/UserSettingsViewModel.kt` — diff only

Constructor: add `private val channelSlotResolver: ChannelSlotResolver`

`pushChannelToNode` — remove `saveLogicalChannel` call:
```kotlin
private fun pushChannelToNode(channel: LogicalChannel, slot: Int) {
    val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return
    val pskBase64 = Base64.encodeToString(binding.psk, Base64.NO_WRAP)
    writeChannel(slot, channel.metadata.name, pskBase64)
    // resolver auto-updates from channelSetFlow — no DB save needed
}
```

`onDeleteFromNode` — slot from resolver:
```kotlin
fun onDeleteFromNode(id: LogicalChannelId) {
    val channel = cachedChannels.find { it.id == id } ?: return
    val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return
    val slot = channelSlotResolver.hashToSlot[binding.channelHash] ?: return
    if (slot == 0) return
    writeChannel(slot, "", "")
}
```

`onEditorSave` — `MeshtasticBinding` now requires `channelHash`:
```kotlin
transports = listOf(
    MeshtasticBinding(
        psk = pskBytes,
        channelHash = LogicalChannelHash.compute(editor.name, pskBytes),
    )
),
```

### DI changes

**`userSettingsModule`** — добавить:
```kotlin
single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }
```

**`chatDataModule`** — добавить `channelSlotResolver = get()` в:
```kotlin
MeshToChatAdapter(...)
IngestReceivedChatMessagesUseCase(...)
```

**`geoMarkDataModule`** — добавить `channelSlotResolver = get()` в:
```kotlin
GeoMarkRepositoryImpl(...)
IngestReceivedGeoMarksUseCase(...)
```

**`MeshDataModule`**:
```kotlin
// было: single { NodeProvisioningUseCase(get(), get(), get(), get(), get()) }
single { NodeProvisioningUseCase(get(), get(), get(), get(), get(), get(), get()) }
```

**`PresentationModule`** — `viewModelOf(::UserSettingsViewModel)` подхватит `ChannelSlotResolver` автоматически.

---

## Phase Plan

### Phase 0 — Research
**Skipped.** SHA-256 in Kotlin (JVM `MessageDigest`) is well-understood. No protocol unknowns.

---

### Phase 1 — Architecture Design ✅ Done

**Output**: Full scaffolding in "Architecture Scaffolding" section above.

> **Token checkpoint**: run `/compact` before Phase 3.

---

### Phase 2 — UI / Icon Design
**Skipped.** No new UI elements.

---

### Phase 3 — Implementation

**Goal**: All affected files updated, compiling, runtime-correct.
**Order**: domain → data → DI → presentation.

See "Architecture Scaffolding" section for exact code for each step.

#### Step 1 — New domain types
- `domain/channel/model/LogicalChannelHash.kt` — `@JvmInline value class` + `compute()`
- `domain/channel/model/ChannelSlotMaps.kt` — data class
- `domain/channel/ChannelSlotResolver.kt` — interface with `slotToHash`, `hashToSlot`, `mapsFlow`
- `domain/channel/model/MeshtasticBinding.kt` — remove `resolvedSlot`, add `channelHash`
- `domain/channel/repository/LogicalChannelRepository.kt` — add `findByChannelHash`

#### Step 2 — DB migration
- `shared/.../4.sqm` — `ALTER TABLE logical_channel ADD COLUMN channel_hash TEXT`
- `LogicalChannel.sq` — add column, `selectByChannelHash`, `updateChannelHash`, update `upsert`

#### Step 3 — Repository + resolver implementation
- `LogicalChannelRepositoryImpl` — persist hash on write, backfill on read, impl `findByChannelHash`
- `FakeLogicalChannelRepository` — add `findByChannelHash`
- `data/channel/ChannelSlotResolverImpl.kt` — singleton, subscribes to `ObserveNodeChannelsUseCase`

#### Step 4 — DI registration
- `userSettingsModule`: `single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }`

#### Step 5 — Ingestion use cases
- `IngestReceivedChatMessagesUseCase` — inject resolver, combine with `mapsFlow`
- `IngestReceivedGeoMarksUseCase` — same
- Update `chatDataModule`, `geoMarkDataModule`

#### Step 6 — Adapters & repositories
- `MeshToChatAdapter` — inject resolver, fix send + contacts + resolveContactKey
- `GeoMarkRepositoryImpl` — inject resolver, fix `resolveChannelId`
- Update `chatDataModule`, `geoMarkDataModule`

#### Step 7 — Settings & provisioning
- `UserSettingsViewModel` — inject resolver, fix `onDeleteFromNode`, `pushChannelToNode`, `onEditorSave`
- `NodeProvisioningUseCase` — add `ObserveNodeChannelsUseCase` + `ResolveChannelSlotUseCase`
- Update `MeshDataModule` (7 args), `PresentationModule` (auto via `viewModelOf`)

**After all steps**: run `/simplify` on changed files.

**Output**: buildable, runtime-correct code.

---

### Phase 4 — Testing

**Goal**: Key behaviors verified at unit level.

**Tests (via `/tester`):**

| Test | What it verifies |
|---|---|
| `computeChannelHash` | Deterministic; stable; different outputs for different inputs |
| `ChannelSlotResolverImpl` | Both maps built correctly from `List<NodeChannelSlot>`; empty list → empty maps |
| `IngestReceivedChatMessagesUseCase` | Message routed to correct channel by slot; unresolved slot → dropped |
| `IngestReceivedGeoMarksUseCase` | Same |
| DB migration backfill | Row with `channel_hash = null` → hash computed and persisted on first load |

**Skill**: `/tester`

---

### Phase 5 — Integration Review

**Goal**: Clean Architecture not violated.

**Skill**: `/architect review:` — verify:
- `ChannelSlotResolver` interface placement (domain, not data)
- Injection of resolver into use cases (domain must not import data)
- Backfill logic placement (repository, not use case)

**Output**: review clean or violations fixed.

---

### Phase 6 — Skill Update Review

- `/architect`: Add `ChannelSlotResolver` as canonical **runtime resolver** pattern — domain interface, data impl, injected into use cases. Document as preferred approach for any future "live node state → domain lookup" needs.
- `/tester`: No new test patterns introduced.
- `/planner`: No methodology gaps.
- `/ui-designer`, `/icon-designer`: No changes needed.

---

### Phase 6b — Docs & Memory Update

- **Update** `.claude/docs/logical-channels-management.md`:
  - Replace `resolvedSlot`-based architecture section with hash-based identity description
  - Update domain model table: remove `resolvedSlot`, add `channelHash`, add `ChannelSlotResolver`
  - Update routing description
  - Add non-obvious decision: why UUID stays as DB PK instead of channelHash
- **Archive**: move this plan to `.claude/archive/logical-channel-identity-refactor.md`
- **CLAUDE.md**: no status change needed (feature already marked Done; this is a refactor)
- **Memory**: update `project_spec.md` — note `channelHash` as channel identity mechanism

---

### Phase 7 — Commit Preparation

- Stage changed files by name (never `git add -A`)
- Commit message in Russian, imperative mood, no `Co-Authored-By`
- Wait for explicit user confirmation before `git commit`

---

## Coordination Map

```
Phase 1: /architect feature: LogicalChannelHash + ChannelSlotResolver → [/compact]
Phase 3: direct coding (EnterPlanMode) → /simplify on changed files
Phase 4: /tester
Phase 5: /architect review:
Phase 6: direct edit .claude/commands/architect.md
Phase 6b: direct edit .claude/docs/logical-channels-management.md + archive + memory
Phase 7: git stage by name → propose commit message → wait confirmation → git commit
```

---

## Blast Radius

8 production files affected:

| File | Change |
|---|---|
| `domain/channel/model/MeshtasticBinding.kt` | Remove `resolvedSlot`, add `channelHash` |
| `data/channel/repository/LogicalChannelRepositoryImpl.kt` | Persist hash, drop slot, add backfill |
| `domain/chat/usecase/IngestReceivedChatMessagesUseCase.kt` | Slot lookup via resolver |
| `data/chat/adapter/MeshToChatAdapter.kt` | Send/contacts/resolve via resolver |
| `domain/marker/usecase/IngestReceivedGeoMarksUseCase.kt` | Slot lookup via resolver |
| `data/marker/repository/GeoMarkRepositoryImpl.kt` | Hash match instead of slot match |
| `domain/mesh/usecase/NodeProvisioningUseCase.kt` | Slot from resolver |
| `presentation/feature/settings/UserSettingsViewModel.kt` | Remove slot copy/clear |

New files:
- `domain/channel/model/LogicalChannelHash.kt`
- `domain/channel/model/ChannelSlotMaps.kt`
- `domain/channel/ChannelSlotResolver.kt`
- `data/channel/ChannelSlotResolverImpl.kt`
- `shared/data/sqldelight/.../4.sqm`

Also touched (interface additions):
- `domain/channel/repository/LogicalChannelRepository.kt`
- `data/channel/repository/FakeLogicalChannelRepository.kt`

---

## Change Log

- 2026-04-23: created from seed, open questions resolved, scope finalized
- 2026-04-23: Phase 1 complete — full architect scaffolding added
