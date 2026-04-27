# Plan: Channel Sync Validation

**Date**: 2026-04-27
**Status**: Approved

## Summary

When the app connects to a node it reads the current channel configuration and compares it against
the app's contours. If any mismatch is detected the user is shown a dialog offering to write
channels + reboot the node. Declining sets a persistent "requires sync" flag shown in the HUD
Radio info slot. The same check runs when the user enables a contour via its checkbox.

## Scope

**In scope:**
- On connect: compare node channels to active contours → dialog on mismatch
- On contour checkbox activation: same comparison (full check, not just the toggled contour)
- "Yes" path: write all channels via `SyncContoursOnConnectUseCase` + reboot node + auto-reconnect
- "No" path: set `syncRequired = true` → HUD Radio info slot shows "требуется синхронизация" in red
- `MeshConfigRepository.rebootNode()` + `RebootNodeUseCase`
- `CheckContourSyncUseCase` — pure compare logic
- `ContourSyncStateRepository` — in-memory flag shared across ViewModels

**Out of scope:**
- Manual sync trigger (deferred per user)
- Inactive contours (isActive = false) — not checked
- Geo broadcast settings verification (already written on every connect, always correct)
- Sync result ACK / confirmation that reboot was received

## Sync Check Rules

1. **Emergency (slot 0)**: `nodeChannels[0].name == DefaultContour.CHANNEL_NAME`
   AND `nodeChannels[0].psk contentEquals Base64.decode(DefaultContour.OPEN_PSK)`.
2. **Active non-emergency contours**: for each `isActive=true && !isEmergency` contour,
   node must have a slot where `ContourHash.compute(slot.name, slot.psk) == contour.transport.meshtastic.channelHash`
   AND `slot.isEnabled == true`.
3. Contours where `isActive=false` → **not checked**.

## On-Connect Flow (new vs old)

**Old**: `UserSettingsViewModel.onConnected()` → `syncContoursOnConnect()` (blind write).

**New**: `MainViewModel` on `justConnected` → `checkContourSync()` →
- InSync → nothing (geo broadcast config still runs separately)
- NeedsSync → `MainUiState.showSyncDialog = true`

`UserSettingsViewModel.onConnected()` keeps only the geo broadcast part
(`enableNodePositionBroadcastReady` / `disableNodePositionBroadcast`).

## Architecture Notes

### New domain additions

| File | Role |
|---|---|
| `domain/channel/usecase/CheckContourSyncUseCase.kt` | Compares contours vs nodeChannels; returns `ContourSyncResult` (InSync / NeedsSync) |
| `domain/channel/model/ContourSyncResult.kt` | `sealed class`: `InSync`, `NeedsSync` |
| `domain/mesh/usecase/RebootNodeUseCase.kt` | Wraps `MeshConfigRepository.rebootNode()` |

### MeshConfigRepository change

Add `fun rebootNode()` to interface + impl:
```kotlin
// MeshConfigRepositoryImpl
override fun rebootNode() {
    val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
    meshRouter.actionHandler.handleRequestReboot(generateRequestId(), myNodeNum)
}
```
`generateRequestId()` → use `commandSender.generatePacketId()` (already available in impl).

### ContourSyncStateRepository

```kotlin
interface ContourSyncStateRepository {
    val syncRequired: Flow<Boolean>
    fun setSyncRequired(value: Boolean)
    fun clear()
}
// Impl: MutableStateFlow<Boolean>(false), @Single DI
```

Shared between `MainViewModel` and `UserSettingsViewModel`. Cleared on each "Да" confirm.

### MainViewModel changes

- On `justConnected` → `viewModelScope.launch { checkContourSync() → update showSyncDialog }`
- Observe `syncStateRepository.syncRequired` → `MainUiState.syncRequired`
- `onConfirmChannelSync()`: `syncContoursOnConnect()` + `rebootNode()` + `syncStateRepository.clear()` + hide dialog
- `onDismissChannelSync()`: `syncStateRepository.setSyncRequired(true)` + hide dialog
- `buildConnectionInfoSlot()`: when `Connected && syncRequired` → `HudInfoSlot("требуется синхронизация", Color.Red)`
  (takes priority over "Настройте канал" and "Сопряжено с" labels)

`MainUiState` additions:
```kotlin
val showSyncDialog: Boolean = false
val syncRequired: Boolean = false
```

### UserSettingsViewModel changes

- `onToggleActive(id, isActive = true)` → after `setContourActive(id, true)` → if connected →
  `checkContourSync()` → if NeedsSync → `_uiState.update { it.copy(showSyncDialog = true) }`
- `onToggleActive(id, isActive = false)` → no check (inactive contours not checked)
- `onConfirmChannelSync()`: `syncContoursOnConnect()` + `rebootNode()` + `syncStateRepository.clear()` + hide dialog
- `onDismissChannelSync()`: `syncStateRepository.setSyncRequired(true)` + hide dialog

`UserSettingsUiState` addition:
```kotlin
val showSyncDialog: Boolean = false
```

### UI additions

- `SyncRequiredDialog` composable (shared or duplicated per screen):
  - Title: "Требуется синхронизация"
  - Message: "Для работы требуется синхронизация настроек ноды. После синхронизации нода будет перезагружена."
  - "Да" → `onConfirm()` / "Нет" → `onDismiss()`
- `MainScreen` / `MapScreen`: show `SyncRequiredDialog` when `state.showSyncDialog`
- `UserTabContent`: show `SyncRequiredDialog` when `state.showSyncDialog`

## Phase Plan

### Phase 1 — Architecture Design

**Goal**: approved class signatures, data flow, DI

**Tasks**:
- Design `CheckContourSyncUseCase` signature and `ContourSyncResult` model
- Design `ContourSyncStateRepository` interface
- Confirm `MeshConfigRepositoryImpl.rebootNode()` approach (uses `commandSender.generatePacketId()`)
- Define where `SyncContoursOnConnectUseCase` is called in the new flow vs old

**Skill**: `/architect feature: channel sync validation — see plan at .claude/plans/channel-sync-validation.md`
**Output**: architecture sign-off (can be inline in conversation, no separate file needed)

### Phase 2 — UI Design

**Goal**: approved `SyncRequiredDialog` composable, HUD slot mapping

**Tasks**:
- `SyncRequiredDialog` — text, button labels, Material3 component choice (AlertDialog)
- HUD Radio info slot priority order (syncRequired vs hasChannelOnNode vs connectionLabel)

**Skill**: `/ui-designer component: SyncRequiredDialog + HUD Radio info slot priority`
**Output**: component spec, updated HUD info slot table

### Phase 3 — Implementation

Order: domain → data → DI → presentation

1. **Domain**
   - `ContourSyncResult.kt` — `sealed class ContourSyncResult { object InSync; object NeedsSync }`
   - `CheckContourSyncUseCase.kt` — pure logic: reads contours + nodeChannels via `first()`, applies rules
   - `RebootNodeUseCase.kt` — wraps `MeshConfigRepository.rebootNode()`
   - `ContourSyncStateRepository.kt` — interface

2. **Data**
   - `MeshConfigRepository.kt` — add `fun rebootNode()`
   - `MeshConfigRepositoryImpl.kt` — implement `rebootNode()`
   - `ContourSyncStateRepositoryImpl.kt` — `MutableStateFlow<Boolean>` impl

3. **DI** (`MeshDataModule.kt` / `userSettingsModule`)
   - Bind `ContourSyncStateRepositoryImpl`, `CheckContourSyncUseCase`, `RebootNodeUseCase`

4. **Presentation**
   - `MainUiState.kt` — add `showSyncDialog`, `syncRequired`
   - `MainViewModel.kt` — on connect check, confirm/dismiss handlers, HUD slot update, syncRequired observer
   - `UserSettingsUiState.kt` — add `showSyncDialog`
   - `UserSettingsViewModel.kt` — toggle check, confirm/dismiss handlers; remove blind `syncContoursOnConnect()` from `onConnected()`
   - `SyncRequiredDialog.kt` — new composable
   - `MainScreen.kt` / `UserTabContent.kt` — wire dialog

**Skill**: direct coding (EnterPlanMode before starting)
**Output**: buildable code; run `/simplify` on changed files after done

### Phase 4 — Testing

**Goal**: core logic verified

**Tasks**:
- `CheckContourSyncUseCase` unit test:
  - InSync: slot 0 matches Emergency, active contours present
  - NeedsSync: slot 0 name wrong
  - NeedsSync: active contour missing from node
  - NeedsSync: contour on node but isEnabled=false
- `ContourSyncStateRepository` unit test: set/clear/observe
- `UserSettingsViewModel` test: `onToggleActive(isActive=true)` → connected → NeedsSync → showSyncDialog emitted

**Skill**: direct coding (FlowUseCase/Turbine for use case; MockK for ViewModel)
**Output**: passing unit tests

### Phase 5 — Integration Review

**Goal**: no Clean Architecture violations

**Tasks**:
- Verify `ContourSyncStateRepository` is in domain (not data) — it's a domain-level state holder
- Verify `CheckContourSyncUseCase` doesn't import Android classes
- Verify `MainViewModel` doesn't reference data layer types directly

**Skill**: `/architect review: domain/channel/usecase/CheckContourSyncUseCase.kt, data/mesh/repository/MeshConfigRepositoryImpl.kt, presentation/feature/main/MainViewModel.kt`
**Output**: review report; violations fixed

### Phase 6 — Skill Update Review

- `/architect`: new pattern — `ContourSyncStateRepository` as in-memory domain-level state bus (similar to `Controllable Background Repository Pattern`); document it
- `/ui-designer`: `SyncRequiredDialog` added; HUD info slot priority order updated
- `/icon-designer`: no changes
- `/planner`: no methodology gaps
- `/tester`: no new test patterns

### Phase 6b — Docs & Memory Update

- CLAUDE.md: no status table change (channel sync is part of Contour feature, already Done — add sub-item or note)
- Create `.claude/docs/channel-sync-validation.md`
- Add plan to archive, delete from `plans/`
- Update `project_state.md` memory

### Phase 7 — Commit Preparation

- `git status` → enumerate changed files
- Stage by name
- Draft commit in Russian, imperative mood
- Present + wait for user confirmation → `git commit`

## Coordination Map

```
Phase 1: /architect feature: channel sync validation
Phase 2: /ui-designer component: SyncRequiredDialog + HUD info slot priority
Phase 3: [direct coding] — domain → data → DI → presentation → /simplify
Phase 4: [direct coding — tests]
Phase 5: /architect review: CheckContourSyncUseCase, MeshConfigRepositoryImpl, MainViewModel
Phase 6: [skill update review]
Phase 6b: [docs & memory — create .claude/docs/channel-sync-validation.md, archive plan, memory/]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Open Questions

1. **`DefaultContour.OPEN_PSK` bytes**: need to verify `nodeChannels[0].psk` comparison — is it
   empty ByteArray (disabled/default) or the single-byte `0x01` AQ== key? Check `DefaultContour.kt`
   before implementing `CheckContourSyncUseCase`.

2. **`generateRequestId()` in `MeshConfigRepositoryImpl.rebootNode()`**: `commandSender` is already
   injected, so `commandSender.generatePacketId()` is usable. Confirm this is correct vs a
   separate ID generator.

3. ~~**HUD info slot priority**~~ **RESOLVED**: `syncRequired` wins over `hasChannelOnNode`.

4. **`syncRequired` persistence**: in-memory only (cleared on app restart, rechecked on next connect).
   If stronger guarantee needed — add DataStore. MVP: in-memory is sufficient.

5. ~~**`SyncContoursOnConnectUseCase` removal from `UserSettingsViewModel`**~~ **RESOLVED**:
   blind write removed; first-run case also goes through the dialog — expected behaviour.

## Change Log

- 2026-04-27: created
