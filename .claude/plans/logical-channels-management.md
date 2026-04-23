# Plan: Logical Channels Management & Node Import

**Date**: 2026-04-23
**Status**: In Progress (revised)

## Summary

Extends logical channel CRUD with node sync capability. User defines `LogicalChannel`
(name + PSK), marks it for auto-sync, and the app finds the matching slot on the
connected radio automatically — no manual slot selection. The app reads current node
channel state, matches by (name + PSK), writes to the first free slot (1–7) if not
found, or reports conflict if all slots are occupied.
Slot 0 (PRIMARY) is never written or deleted by this feature.
Deferred: user-to-user channel export/import (QR / deep link).

---

## Scope

**In scope:**
- Remove `SlotDropdown` from `ChannelEditorSheet`; remove `slotIndex` from `ChannelEditorState`
- Add `isAutoSync: Boolean` to `LogicalChannel` domain model + DB migration
- `MeshtasticBinding.channelIndex` → `resolvedSlot: Int?` (nullable; populated from node scan)
- New domain model: `NodeChannelSlot` + `ChannelSyncStatus`
- `MeshConfigRepository.observeNodeChannels(): Flow<List<NodeChannelSlot>>`
- ViewModel observes node channels → computes `ChannelSyncStatus` per channel
- Auto-sync: on connect + on node channel update → write channels with `isAutoSync=true` and `syncStatus=NotOnNode`
- Manual push: "Записать в ноду" still available per-channel from dropdown
- "Удалить из ноды" action (slots 1–7 only; slot 0 blocked)
- `isAutoSync` checkbox on `ChannelCard`
- `ChannelSyncStatus` badge on `ChannelCard` (replaces old `transportLabel`)
- `NoFreeSlot` badge when slots 1–7 all occupied; management UX → TODO (see Open Questions)
- Slot 0 is read-only: never written, never deleted, skipped in free-slot search
- TODO block: user-to-user exchange (QR / deep link)
- TODO block: "manage node slots" sheet for `NoFreeSlot` case
- TODO block: write confirmation dialog + loading/result indicators (see Open Questions)

- **HUD info slot** (radio button row): when connected AND user has ≥1 LogicalChannel AND
  none are on the node → show "Настройте канал" in red; otherwise normal behavior

**Out of scope:**
- ACK confirmation from radio (fire-and-forget; ACK tracking is a future enhancement)
- "Manage node slots" sheet implementation — deferred
- User-to-user channel exchange — deferred
- Moving channel management to a different screen or tab

---

## Architecture Notes

### What already exists

| Artifact | Location |
|---|---|
| `WriteChannelUseCase` | `domain/mesh/usecase/WriteChannelUseCase.kt` |
| `MeshConfigRepository.writeChannel(index, name, pskBase64)` | `domain/mesh/repository/MeshConfigRepository.kt` |
| `ObserveConnectionStatusUseCase` | `domain/mesh/usecase/ObserveConnectionStatusUseCase.kt` |
| `CommandSender.channelSetFlow: StateFlow<ChannelSet>` | `mesh/repository/CommandSender.kt` |
| `LogicalChannel` + `MeshtasticBinding` | `domain/channel/model/` |
| `LogicalChannelRepository` (SQLDelight) | `data/channel/repository/LogicalChannelRepositoryImpl.kt` |
| `UserSettingsViewModel` (CRUD) | `presentation/feature/settings/UserSettingsViewModel.kt` |
| `UserTabContent` + `ChannelCard` | `presentation/feature/settings/UserTabContent.kt` |

### Domain model changes

**`MeshtasticBinding`** — `channelIndex` becomes optional resolved cache:

```kotlin
data class MeshtasticBinding(
    val psk: ByteArray,
    val resolvedSlot: Int? = null,  // null = channel not yet found on node
) : TransportBinding {
    // equals/hashCode on psk only (slot is derived state)
}
```

> The chat/messaging layer uses slot index for routing. It reads `resolvedSlot` from
> the cached binding. If `null`, messages on this channel cannot be sent until the
> channel is synced to the node and a slot is assigned.

**`LogicalChannel`** — add `isAutoSync`:

```kotlin
data class LogicalChannel(
    val id: LogicalChannelId,
    val metadata: ChannelMetadata,
    val transports: List<TransportBinding>,
    val isAutoSync: Boolean = false,
)
```

### New domain models

```
domain/channel/model/
├── NodeChannelSlot.kt      — represents one slot on the physical radio
└── ChannelSyncStatus.kt    — per-channel sync state for UI

domain/channel/usecase/
└── ResolveChannelSlotUseCase.kt  — pure logic: find slot for a channel on node
```

**`NodeChannelSlot`:**
```kotlin
data class NodeChannelSlot(
    val index: Int,
    val name: String,
    val psk: ByteArray,
    val isEnabled: Boolean,   // false = role DISABLED (free slot)
)
```

**`ChannelSyncStatus`:**
```kotlin
sealed interface ChannelSyncStatus {
    data object NotConnected : ChannelSyncStatus
    data class OnNode(val slot: Int) : ChannelSyncStatus   // matched by name+PSK
    data object NotOnNode : ChannelSyncStatus              // connected, not found on node
    data object NoFreeSlot : ChannelSyncStatus             // slots 1–7 all occupied
}
```

**`ResolveChannelSlotUseCase`** (pure, no repo dependency):
```kotlin
// Returns:
sealed interface SlotResolution {
    data class AlreadySynced(val slot: Int) : SlotResolution
    data class FreeSlot(val slot: Int) : SlotResolution      // first available slot 1–7
    data object NoFreeSlot : SlotResolution
}

// Logic:
// 1. find nodeChannels where name == channel.name && psk == binding.psk  → AlreadySynced
// 2. find nodeChannels where index >= 1 && !isEnabled                    → FreeSlot(index)
// 3. otherwise                                                            → NoFreeSlot
```

> Slot 0 is always excluded from search (never free, never assigned).

### `MeshConfigRepository` — new method

```kotlin
fun observeNodeChannels(): Flow<List<NodeChannelSlot>>
```

`MeshConfigRepositoryImpl` maps from `commandSender.channelSetFlow`:
```kotlin
override fun observeNodeChannels(): Flow<List<NodeChannelSlot>> =
    commandSender.channelSetFlow.map { channelSet ->
        channelSet.settings.mapIndexed { index, settings ->
            // channelSet.settings is a List<ChannelSettings>; role inferred from index
            NodeChannelSlot(
                index = index,
                name = settings.name,
                psk = settings.psk.toByteArray(),
                isEnabled = settings.psk.size > 0 || index == 0,
            )
        }
    }
```

> Note: actual enablement logic depends on proto ChannelSet structure.
> Verify against `getCachedChannelSet()` during implementation.

### Updated `ChannelItem`

```kotlin
data class ChannelItem(
    val id: LogicalChannelId,
    val name: String,
    val isAutoSync: Boolean,
    val syncStatus: ChannelSyncStatus,   // replaces transportLabel
)
```

### Updated `ChannelEditorState`

```kotlin
data class ChannelEditorState(
    val id: LogicalChannelId?,
    val name: String,
    val pskBase64: String,
    // slotIndex removed — slot is auto-resolved
)
```

### DB migration — `3.sqm`

```sql
ALTER TABLE logical_channel ADD COLUMN is_auto_sync INTEGER NOT NULL DEFAULT 0;
-- meshtastic_slot semantics change: now stores resolved slot cache (read-only from UI)
-- no rename needed; document in LogicalChannelRepositoryImpl
```

`LogicalChannel.sq` — add `is_auto_sync` column + update queries:
```sql
CREATE TABLE logical_channel (
    id              TEXT    NOT NULL PRIMARY KEY,
    name            TEXT    NOT NULL,
    meshtastic_slot INTEGER,           -- resolved slot cache; null = not on node
    meshtastic_psk  BLOB,
    is_auto_sync    INTEGER NOT NULL DEFAULT 0
);
```

### `UserSettingsViewModel` — extended responsibilities

New dependencies:
```kotlin
private val observeNodeChannels: ObserveNodeChannelsUseCase,  // or direct via MeshConfigRepository
private val writeChannel: WriteChannelUseCase,
private val resolveSlot: ResolveChannelSlotUseCase,
private val observeConnectionStatus: ObserveConnectionStatusUseCase,
```

Private state (not in UiState — PSK must not leak to UI):
```kotlin
private var cachedChannels: List<LogicalChannel> = emptyList()
private var cachedNodeChannels: List<NodeChannelSlot> = emptyList()
private var connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected
```

**Auto-sync trigger** — combine channels + node state:
```kotlin
combine(
    observeLogicalChannels(NoParams),
    observeNodeChannels(NoParams),
    observeConnectionStatus(NoParams),
) { channels, nodeChannels, status ->
    cachedChannels = channels
    cachedNodeChannels = nodeChannels
    connectionStatus = status

    // compute sync status per channel
    val items = channels.map { ch ->
        val syncStatus = computeSyncStatus(ch, nodeChannels, status)
        ChannelItem(ch.id, ch.metadata.name, ch.isAutoSync, syncStatus)
    }
    _uiState.update { it.copy(channels = items) }

    // trigger auto-sync
    if (status is MeshConnectionStatus.Connected) {
        channels
            .filter { it.isAutoSync }
            .forEach { ch ->
                val resolution = resolveSlot(ch, nodeChannels)
                if (resolution is SlotResolution.FreeSlot) {
                    pushChannelToNode(ch, resolution.slot)
                }
            }
    }
}
```

New ViewModel methods:
```kotlin
fun onPushToNode(id: LogicalChannelId)        // manual push; resolves slot, writes
fun onDeleteFromNode(id: LogicalChannelId)    // sends DISABLED to resolved slot; blocked if slot == 0
fun onToggleAutoSync(id: LogicalChannelId, enabled: Boolean)
fun onNodeWriteEventConsumed()
```

**Delete from node:**
```kotlin
fun onDeleteFromNode(id: LogicalChannelId) {
    val channel = cachedChannels.find { it.id == id } ?: return
    val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return
    val slot = binding.resolvedSlot ?: return
    require(slot != 0) { "Slot 0 cannot be deleted" }  // hard guard
    // writeChannel with empty name/psk → role=DISABLED
    writeChannel(slot, name = "", pskBase64 = "")
    // update resolvedSlot to null in DB
    viewModelScope.launch {
        saveLogicalChannel(channel.copy(
            transports = listOf(binding.copy(resolvedSlot = null))
        ))
    }
}
```

### UiState changes

```kotlin
// models/NodeWriteEvent.kt
sealed interface NodeWriteEvent {
    data class Sent(val channelName: String) : NodeWriteEvent
    data object NotConnected : NodeWriteEvent
    data object NoFreeSlot : NodeWriteEvent
}

// UserSettingsUiState.kt additions:
val nodeWriteEvent: NodeWriteEvent? = null
```

### `ChannelCard` UI changes

- Replace `transportLabel` + badge → `syncStatus` badge:

| `syncStatus` | Badge |
|---|---|
| `OnNode(slot)` | "На ноде · слот N" (secondary container) |
| `NotOnNode` | "Не на ноде" (error container) |
| `NoFreeSlot` | "Нет слотов" (error container) |
| `NotConnected` | *(no badge)* |

- Add checkbox (leading): `isAutoSync` toggle → calls `onToggleAutoSync`
- Dropdown items (context-sensitive):
  - "Записать в ноду" — always shown (for manual push)
  - "Удалить из ноды" — only when `syncStatus is OnNode && slot != 0`
  - "Редактировать" — existing
  - "Удалить" — existing

### HUD info slot — "Настройте канал"

**Trigger condition:**
```
status is Connected
AND cachedChannels.isNotEmpty()           ← user has configured channels in app
AND cachedChannels.none { synced to node } ← none of them are on this node
```

If user has 0 logical channels → normal HUD behavior (no warning; they haven't set up
channels yet, not an error state).

**Data flow:**

`MainViewModel` already observes `connectionStatus`. Needs two new inputs:
- `ObserveLogicalChannelsUseCase` (already exists)
- `observeNodeChannels` (new — added in this feature)

Compute derived flag:
```kotlin
// In MainViewModel
private val hasChannelOnNode = MutableStateFlow(true)  // true = no warning shown

combine(observeLogicalChannels(NoParams), observeNodeChannels()) { channels, nodeSlots ->
    if (channels.isEmpty()) return@combine true          // no channels configured — not an error
    val anyMatch = channels.any { ch ->
        val binding = ch.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return@any false
        nodeSlots.any { slot ->
            slot.isEnabled && slot.name == ch.metadata.name && slot.psk.contentEquals(binding.psk)
        }
    }
    anyMatch
}.onEach { hasChannelOnNode.value = it }.launchIn(viewModelScope)
```

Add to `MainUiState`:
```kotlin
val hasChannelOnNode: Boolean = true  // true = OK; false = show warning
```

Update `buildConnectionInfoSlot` in `MainViewModel`:
```kotlin
is MeshConnectionStatus.Connected ->
    if (!state.hasChannelOnNode)
        HudInfoSlot(content = "Настройте канал", color = Color.Red)
    else if (state.showConnectionLabel)
        HudInfoSlot(content = "Сопряжено с ${status.shortName}", color = Color.Green)
    else
        emptyInfoSlot()
```

> "Настройте канал" is persistent — stays red until user pushes at least one channel
> to the node (auto or manual). As soon as `observeNodeChannels` reflects the new slot,
> `hasChannelOnNode` flips to `true` and info slot clears.

**`observeNodeChannels` in MainViewModel**: wrap via `MeshConfigRepository.observeNodeChannels()`
or expose as a new `ObserveNodeChannelsUseCase`. Inject into both `MainViewModel` and
`UserSettingsViewModel` — same source, same logic.

### Strings to add

```xml
<string name="user_channel_push_to_node">Записать в ноду</string>
<string name="hud_radio_setup_channel">Настройте канал</string>
<string name="user_channel_delete_from_node">Удалить из ноды</string>
<string name="channel_sync_on_node">На ноде · слот %d</string>
<string name="channel_sync_not_on_node">Не на ноде</string>
<string name="channel_sync_no_free_slot">Нет слотов</string>
<string name="channel_auto_sync_label">Авто-синхронизация</string>
```

---

## Phase Plan

### Phase 0 — Research (targeted)
- **Goal**: confirm proto `ChannelSet` structure for `observeNodeChannels` mapping
- **Tasks**: read `getCachedChannelSet()` return type; verify `Channel.role` vs `ChannelSettings` fields
- **Skill**: inline code read (no `/research` subagent — already in codebase)
- **Output**: confirmed mapping logic in Architecture Notes above

### Phase 1 — Architecture
- **Goal**: approved design (this document)
- **Skill**: `/architect review:` if needed after Phase 0 clarification
- **Output**: this doc approved

### Phase 2 — UI Design (minimal)
- **Goal**: confirm badge colors, checkbox placement, wording
- **Tasks**: no new components; inline decisions
- **Output**: strings confirmed (§ Strings to add)

### Phase 3 — Implementation (ordered)

**Domain layer:**
1. Update `MeshtasticBinding`: `channelIndex: Int` → `resolvedSlot: Int?`
2. Add `isAutoSync: Boolean` to `LogicalChannel`
3. New file `domain/channel/model/NodeChannelSlot.kt`
4. New file `domain/channel/model/ChannelSyncStatus.kt`
5. New file `domain/channel/usecase/ResolveChannelSlotUseCase.kt`
6. Add `observeNodeChannels()` to `MeshConfigRepository` interface

**Data layer:**
7. Update `LogicalChannel.sq`: add `is_auto_sync` column; update `upsert` query
8. Create `3.sqm` migration
9. Update `LogicalChannelRepositoryImpl`: map `is_auto_sync`; rename slot semantics in comments
10. Implement `MeshConfigRepositoryImpl.observeNodeChannels()`

**Presentation model:**
11. Update `ChannelItem`: remove `transportLabel`, add `isAutoSync` + `syncStatus`
12. New file `models/NodeWriteEvent.kt`
13. Remove `slotIndex` from `ChannelEditorState`

**ViewModel:**
14. Update `UserSettingsViewModel`:
    - add `cachedChannels`, `cachedNodeChannels`, `connectionStatus` private vars
    - replace triple `launchIn` observers with single `combine`
    - add `onPushToNode`, `onDeleteFromNode`, `onToggleAutoSync`, `onNodeWriteEventConsumed`
    - add slot 0 guard in `onDeleteFromNode`
15. Update `UserSettingsModule` DI: add new deps

**Domain — new use case:**
16. New file `domain/channel/usecase/ObserveNodeChannelsUseCase.kt` — wraps `MeshConfigRepository.observeNodeChannels()`

**MainViewModel — HUD warning:**
17. Add `ObserveLogicalChannelsUseCase` + `ObserveNodeChannelsUseCase` deps to `MainViewModel`
18. Add `hasChannelOnNode: Boolean` to `MainUiState`
19. Add `combine` observer in `MainViewModel.init` computing `hasChannelOnNode`
20. Update `buildConnectionInfoSlot`: add `!state.hasChannelOnNode` branch → `HudInfoSlot("Настройте канал", Color.Red)`

**UI:**
21. Update `ChannelEditorSheet`: remove `SlotDropdown`; remove `slotIndex` param
22. Update `ChannelCard`: add checkbox, sync badge, new dropdown items
23. Update `UserTabContent`: snackbar for `nodeWriteEvent`
24. Add string resources
25. Add TODO comments (user-to-user, manage-slots, write-UX)

### Phase 4 — Testing

Unit test cases for `ResolveChannelSlotUseCase`:
- channel found on node (name+PSK match) → `AlreadySynced`
- channel not found, free slot available → `FreeSlot(first free index 1–7)`
- channel not found, all slots 1–7 occupied → `NoFreeSlot`
- slot 0 never returned as free

Unit test cases for `MainViewModel` HUD warning:
- Connected + empty logical channels → `hasChannelOnNode = true` (no warning)
- Connected + channels exist + none on node → `hasChannelOnNode = false`
- Connected + channels exist + one matches node slot → `hasChannelOnNode = true`
- Not connected → `hasChannelOnNode = true` (warning irrelevant when disconnected)

Unit test cases for `UserSettingsViewModel`:
- `onPushToNode`: not connected → `NotConnected` event
- `onPushToNode`: connected, `AlreadySynced` → no write, `Sent` event
- `onPushToNode`: connected, `FreeSlot` → `writeChannel` called with correct index
- `onPushToNode`: connected, `NoFreeSlot` → `NoFreeSlot` event
- `onDeleteFromNode`: slot 0 → no-op / guarded
- `onDeleteFromNode`: slot 3 → `writeChannel(3, "", "")` called
- `onToggleAutoSync` → `saveLogicalChannel` called with updated `isAutoSync`

### Phase 5 — Integration Review
- **Skill**: `/architect review: domain/channel/ data/channel/ presentation/feature/settings/`

### Phase 6 — Skill Update Review
- `/architect`: document `ResolveChannelSlotUseCase` pattern (pure domain logic, no deps)
- `/ui-designer`: no new components
- `/planner`: no gaps

### Phase 6b — Docs & Memory
- Create `.claude/docs/logical-channels-management.md`
- Archive this plan
- Update `CLAUDE.md` status table

### Phase 7 — Commit Preparation
- Staged files: all modified domain/data/presentation/di files + migration
- Proposed message: `feat(channels): управление каналами и авто-синхронизация с нодой`

---

## Coordination Map

```
Phase 0: [inline code read — confirm ChannelSet proto structure]
Phase 1: [this doc approved] → /architect review if needed
Phase 2: [inline decisions]
Phase 3: [direct coding] — domain first, then data, then presentation
Phase 4: [direct coding — tests for ResolveChannelSlotUseCase + ViewModel]
Phase 5: /architect review: domain/channel/ data/channel/ presentation/feature/settings/
Phase 6: [skill update review]
Phase 6b: [docs & memory]
Phase 7: [stage by name] → [propose commit] → [confirm] → git commit
```

---

## Open Questions

### Resolved

1. **Slot 0 protection**: Hard rule — slot 0 never written, never deleted, skipped in
   free-slot search. Node's PRIMARY channel is not managed by this app.

2. **`MeshtasticBinding` slot field**: Renamed to `resolvedSlot: Int?`.
   Nullable = "not currently on any node slot". Populated from node scan, not user input.

3. **Auto-sync trigger**: Fires inside ViewModel on `combine(channels, nodeChannels, status)`.
   No separate background service for MVP.

### Unresolved (documented as TODO)

4. **Write confirmation dialog**: Should "Записать в ноду" prompt the user before sending?
   Especially relevant if overwriting a slot that has a different channel.
   → TODO: implement after MVP; default is fire-and-forget for now.

5. **Loading indicator**: `writeChannel` is fire-and-forget; no ACK callback exists.
   Showing a spinner would require implementing an ACK tracking mechanism in `CommandSender`.
   → TODO: implement ACK tracking as a separate feature.

6. **"Manage node slots" sheet** (for `NoFreeSlot` case): Show all 8 node slots with their
   names; allow user to mark one (non-0) for deletion; on confirm send DISABLED AdminMessage.
   → TODO: design and implement after core node import is stable.

7. **Result indication for failed write**: Currently no way to detect failure (BLE disconnect
   mid-write, etc.). Need ACK tracking (#5) first.
   → TODO: same dependency as #5.

8. **Deleting slot 0 — UX protection**: Even if hard-blocked in code, should UI hide
   "Удалить из ноды" for slot 0, or show it disabled with a tooltip?
   → Resolve during Phase 3 UI implementation. Preference: hide (not disable).

9. **`ChannelSyncStatus` when channel has no PSK** (blank/empty): Is a channel with no PSK
   a valid `MeshtasticBinding`? Currently editor blocks save if PSK invalid.
   → No action needed; editor validation already guards this case.

---

## TODO comments to add in code

```kotlin
// TODO(channels): user-to-user channel import/export
//   - Export: encode LogicalChannel as URL (meshtactics://channel?name=…&psk=…)
//   - Import: deep link or QR scan → parse → pre-fill ChannelEditorSheet

// TODO(channels): "manage node slots" sheet for NoFreeSlot case
//   Show all 8 slots, allow user to mark one (non-0) for clearing

// TODO(channels): write confirmation dialog before pushing to node

// TODO(channels): loading + result indication requires ACK tracking in CommandSender
```

---

## Change Log

- 2026-04-23: created (v1 — basic push-to-node with explicit slot)
- 2026-04-23: revised (v2) — auto-slot resolution, remove slot picker, add isAutoSync checkbox,
  add delete-from-node, slot 0 hard protection, open UX questions documented
- 2026-04-23: revised (v3) — add HUD info slot "Настройте канал" warning; ObserveNodeChannelsUseCase
  shared between MainViewModel and UserSettingsViewModel
