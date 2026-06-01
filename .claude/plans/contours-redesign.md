# Plan: Contours Redesign — Primary Mechanic + Exclusive Mode

**Date**: 2026-05-29 (updated 2026-06-01)
**Status**: Approved — expanded scope (isolation guarantees)

## Summary

Reworks the Contour feature to introduce a **Primary Contour** concept (exactly one contour owns
slot 0), decouple Emergency's `isActive` from SOS mode, and add an **Exclusive Contour** mechanic
that forces all other contours inactive and clears foreign node slots. Also updates the UI from
checkbox toggles to a radio-button Primary selector with a per-contour dropdown menu.

**Expanded scope (2026-06-01):** Full group isolation guarantees — inactive contours hide node
markers on map, block incoming geo/chat, prevent position leak. Emergency operates in silent mode
outside SOS (store-only, no notifications, no map markers).

Design source: `.claude/docs/contours.md`

---

## Scope

**In scope:**
- `ContourRepository` interface: replace `observeEmergencyIsActive/setEmergencyActive` with
  `observeSosMode/setSosMode`; add `observePrimaryContourId`, `getPrimaryContourId`,
  `setPrimaryContour`, `getPreSosPrimaryId`, `savePreSosPrimaryId`
- `ContourRepositoryImpl`: new DataStore keys (`primary_contour_id`, `sos_mode_active`,
  `pre_sos_primary_id`); Emergency always emitted with `isActive = true` (not from DataStore)
- `GeoSendPolicyImpl`: `observeSosMode()` instead of `observeEmergencyIsActive()`
- `ObserveEmergencyModeUseCase`: wrap `observeSosMode()`
- `EmergencyPositionBroadcastRepositoryImpl`: auto-start reads `observeSosMode()`
- `SetContourActiveUseCase`: remove Emergency special-case; add guard (cannot deactivate Primary)
- `SyncContoursOnConnectUseCase`: slot 0 = primary, slot 1 = Emergency (always), slots 2–7 = active
  non-emergency non-primary; free-slot resolution excludes reserved slots 0 and 1
- `TriggerEmergencyUseCase`: save `pre_sos_primary_id` → `setSosMode(true)` →
  `setPrimaryContour(Emergency.id)` → broadcast + distress message
- `CancelEmergencyUseCase`: stop broadcast → all-clear message → restore `pre_sos_primary_id` →
  `setSosMode(false)`
- New `SetPrimaryContourUseCase`: save `primary_contour_id`; if connected → `writeChannel(0, ...)`
- New `ActivateExclusiveContourUseCase`: set Primary + deactivate all non-emergency + clear
  slots 2–7 on node
- `ContourItem` presentation model: add `isPrimary: Boolean`
- `UserSettingsUiState`: add `primaryContourId: ContourId?`
- `UserSettingsViewModel`: subscribe to `observePrimaryContourId`, expose `onSetPrimary(id)`
- `UserTabContent`: replace checkbox with radio-button for Primary; per-contour DropdownMenu
- `FakeContourRepository`: update to new interface
- DI (`userSettingsModule`): register new use cases
- Tests: update broken unit tests; add new tests for `SetPrimaryContourUseCase`,
  `ActivateExclusiveContourUseCase`, revised `TriggerEmergencyUseCase/CancelEmergencyUseCase`

**New in scope (isolation):**
- `MeshNodeModel.receivedOnSlot: Int?` + tracking via incoming position packets
- `ObserveNodeMarkersUseCase` — contour filter by slot + SOS mode
- `ObserveGeoNodesUseCase` — same filter
- `IngestReceivedGeoMarksUseCase` — fix Emergency routing (slot 1, not slot 0); SOS gate
- `MeshToChatAdapter` — Emergency silent mode (no notification, no unread counter) outside SOS
- `GeoSendPolicyImpl` — fix inverted logic (Step 3.4 correction)

**Testing protocol (не код, процесс):**
- Phase 3.5: перед ручным тестом — временная замена констант Emergency-канала в `DefaultContour.kt`
- Phase 4.5: после подтверждения тестировщика — возврат констант к дефолту; план не закрывается без этого шага

**Out of scope:**
- `ActivateExclusiveContourUseCase` UI trigger (deferred to next task)
- Custom contour creation (waiting for QR/import sharing)
- No-free-slot UI notification
- `exclusivityTime` expiry logic in UI
- signal-tag parsing (future)

---

## Architecture Notes

See `.claude/docs/contours.md` for full design.

### Key decisions

**DataStore keys** (all in `contour_ds`):
```
primary_contour_id   StringPreferencesKey   default = DefaultActiveContour.ID.value
sos_mode_active      BooleanPreferencesKey  default = false
pre_sos_primary_id   StringPreferencesKey?  null when SOS inactive
```

**Emergency `isActive` is not stored.** `ContourRepositoryImpl.observeContours()` prepends
Emergency with `isActive = true` hardcoded. Old `EMERGENCY_IS_ACTIVE_KEY` DataStore key is dropped;
SOS state migrates to `sos_mode_active` (worst case: resets to false on first post-upgrade run,
acceptable).

**Slot reservation:** slots 0 and 1 are reserved (primary + Emergency). `ResolveChannelSlotUseCase`
already receives `usedSlots: MutableSet<Int>` — call site pre-seeds `usedSlots = mutableSetOf(0, 1)`
before iterating active non-primary contours.

**`SetPrimaryContourUseCase` signature:**
```kotlin
suspend operator fun invoke(contourId: ContourId)
```
Saves DataStore + always calls `writeChannel(0, name, psk)`.
Safe when disconnected: `writeChannel` returns early via `myNodeNum ?: return` guard in impl.
Emergency case: name = `DefaultContour.CHANNEL_NAME`, psk = `DefaultContour.OPEN_PSK`.

**`ActivateExclusiveContourUseCase` signature:**
```kotlin
suspend operator fun invoke(contourId: ContourId)
```
`writeChannel` calls are always safe when disconnected (see `SetPrimaryContourUseCase` note).
1. `setPrimaryContour(contourId)` (via repo, not use case — avoids double writeChannel)
2. `observeContours().first()` → for each `!isEmergency && id != contourId` → `saveContour(copy(isActive=false))`
3. If `isConnected`:
   - `writeChannel(0, exclusiveName, exclusivePsk)`
   - `writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)`
   - for `i in 2..7` → `writeChannel(i, "", "")` (empty name + blank psk → disabled SECONDARY slot)

**`SyncContoursOnConnectUseCase` revised logic:**
```
primaryId    = contourRepository.getPrimaryContourId()
primaryContour = contours.find { it.id == primaryId }

// slot 0 = primary
val primaryName = if (primaryContour.isEmergency) DefaultContour.CHANNEL_NAME else primaryContour.name
val primaryPsk  = if (primaryContour.isEmergency) DefaultContour.OPEN_PSK  else primaryContour.transport.meshtastic.psk
if (nodeChannels[0].hash != primaryContour.channelHash) writeChannel(0, primaryName, primaryPsk)

// slot 1 = Emergency (always, if primary is not Emergency)
if (!primaryContour.isEmergency) {
    if (nodeChannels[1]?.hash != DefaultContour.CHANNEL_HASH) writeChannel(1, EMERGENCY_NAME, EMERGENCY_PSK)
}

// slots 2-7 = active non-primary non-emergency
usedSlots = mutableSetOf(0, 1)
for each isActive non-primary non-emergency contour:
    AlreadySynced → usedSlots.add(slot)
    FreeSlot(N)   → writeChannel(N, name, psk); usedSlots.add(N)
    NoFreeSlot    → Log.w, return
```
Edge case: if primary IS Emergency (SOS active), slot 1 is not reserved for Emergency separately —
Emergency is already on slot 0. Slots 1–7 go to active non-emergency.

---

## Phase Plan

### Phase 0 — Research
**Skip.** Domain fully understood; Meshtastic channel write behavior validated in prior session.

---

### Phase 1 — Architecture (light review)
**Goal:** Confirm scaffolding from `contours.md`; no design surprises.
**Tasks:**
- Review `ContourRepository` new interface against existing callers (catch all call-sites
  of `observeEmergencyIsActive` / `setEmergencyActive`)
- Confirm `ResolveChannelSlotUseCase` usedSlots pre-seeding approach is safe
- Confirm `ActivateExclusiveContourUseCase` clearing slot via `writeChannel(i, "", "")` is
  correct Meshtastic protocol (empty name + empty PSK = disabled SECONDARY)
**Skill:** direct code review (no `/architect` needed — design already finalized)
**Output:** confirmed call-site list; any blockers flagged before coding starts

Known callers of `observeEmergencyIsActive` / `setEmergencyActive`:
- `ContourRepositoryImpl` (observeContours combine)
- `GeoSendPolicyImpl`
- `ObserveEmergencyModeUseCase`
- `EmergencyPositionBroadcastRepositoryImpl` (init auto-start)
- `SetContourActiveUseCase`
- `TriggerEmergencyUseCase`
- `CancelEmergencyUseCase`
- `FakeContourRepository`

> **Token checkpoint:** run `/compact` before Phase 2.

---

### Phase 2 — UI Design
**Goal:** Approved design for Primary radio button + per-contour dropdown.
**Tasks:**
- Check Design System for radio-button token (Material3 `RadioButton`)
- Define ContourItem card layout: name + Primary badge + isActive dim + sync status chip
- Define DropdownMenu actions per contour type:
  - Regular active non-primary: `Set as Primary`, `Disable`, `Push to node`, `Delete`
  - Regular inactive: `Set as Primary`, `Enable`, `Push to node`, `Delete`
  - Primary: `Push to node` (Set as Primary + Disable greyed or hidden)
  - Emergency: SOS button only (no active toggle, no Set as Primary)
- Define "Primary" visual indicator (badge / label style)
**Skill:** `/ui-designer component: ContourItem card redesign — radio-button for Primary,
dropdown menu, inactive dim state`
**Output:** approved component spec; token decisions recorded in Design System

---

### Phase 3 — Implementation
**Goal:** Working code across all layers. Build must pass.
**Coding order (strict — each step unblocks the next):**

#### Step 3.1 — `ContourRepository` interface
File: `domain/channel/repository/ContourRepository.kt`
- Remove: `observeEmergencyIsActive()`, `setEmergencyActive()`
- Add: `observePrimaryContourId()`, `getPrimaryContourId()`, `setPrimaryContour()`,
  `observeSosMode()`, `setSosMode()`, `getPreSosPrimaryId()`, `savePreSosPrimaryId()`

#### Step 3.2 — `ContourRepositoryImpl`
File: `data/channel/repository/ContourRepositoryImpl.kt`
- Drop `EMERGENCY_IS_ACTIVE_KEY`; add `PRIMARY_CONTOUR_ID_KEY`, `SOS_MODE_ACTIVE_KEY`,
  `PRE_SOS_PRIMARY_ID_KEY` to `contour_ds` DataStore
- `observeContours()`: stop combining DataStore emergency flag; hardcode `isActive = true`
  for Emergency prepend
- Implement all new interface methods
- No DB migration needed (new keys are DataStore only)

#### Step 3.3 — `FakeContourRepository`
File: `data/channel/repository/FakeContourRepository.kt`
- Implement all new interface methods with in-memory state (for tests)

#### Step 3.4 — `GeoSendPolicyImpl` ⚠️ CORRECTION
File: `data/mesh/GeoSendPolicyImpl.kt`
- ~~`observeAllowed()` = `contourRepository.observeSosMode().map { !it }`~~ — **BUG**: инвертирована логика, блокирует гео при активном SOS
- Correct: `observeAllowed() = flowOf(true)`
- Rationale: гео всегда отправляется на slot 0 (Primary). В SOS-режиме Primary = Emergency → гео на LongFast автоматически. Блокировка по SOS-флагу не нужна и вредна.

#### Step 3.5 — `ObserveEmergencyModeUseCase`
File: `domain/emergency/usecase/ObserveEmergencyModeUseCase.kt`
- Wrap `contourRepository.observeSosMode()`

#### Step 3.6 — `EmergencyPositionBroadcastRepositoryImpl`
File: `data/emergency/EmergencyPositionBroadcastRepositoryImpl.kt`
- `init` auto-start: read `contourRepository.observeSosMode().first()` instead of
  `observeEmergencyIsActive().first()`

#### Step 3.7 — `SetContourActiveUseCase`
File: `domain/channel/usecase/SetContourActiveUseCase.kt`
- Remove `if (id == DefaultContour.ID)` special case (Emergency is never toggled)
- Add guard: if `!isActive` and `id == contourRepository.getPrimaryContourId()` → return (no-op)

#### Step 3.8 — `SetPrimaryContourUseCase` (new)
File: `domain/channel/usecase/SetPrimaryContourUseCase.kt`
- Constructor: `ContourRepository`, `WriteChannelUseCase`
- `invoke(id: ContourId)`: save primaryContourId; resolve contour (handle Emergency special name/psk);
  always call writeChannel(0, ...) — safe when disconnected (no-op guard in impl)

#### Step 3.9 — `ActivateExclusiveContourUseCase` (new)
File: `domain/channel/usecase/ActivateExclusiveContourUseCase.kt`
- Constructor: `ContourRepository`, `WriteChannelUseCase`
- `invoke(id: ContourId)`: set Primary → deactivate all non-emergency → writeChannel(0) + writeChannel(1, Emergency) + writeChannel(2..7, "", "") — see architecture notes

#### Step 3.10 — `SyncContoursOnConnectUseCase`
File: `domain/channel/usecase/SyncContoursOnConnectUseCase.kt`
- Rewrite per revised sync logic in Architecture Notes
- Pre-seed `usedSlots = mutableSetOf(0, 1)` before free-slot iteration
- Add `ContourRepository` to constructor

#### Step 3.11 — `TriggerEmergencyUseCase`
File: `domain/emergency/usecase/TriggerEmergencyUseCase.kt`
- Replace `setEmergencyActive(true)` with:
  `savePreSosPrimaryId(getPrimaryContourId())` → `setSosMode(true)` → `setPrimaryContour(Emergency.id)`
  (setPrimaryContour calls writeChannel internally — safe when disconnected)
- Add `SetPrimaryContourUseCase` to constructor; remove direct `contourRepository.setEmergencyActive`

#### Step 3.12 — `CancelEmergencyUseCase`
File: `domain/emergency/usecase/CancelEmergencyUseCase.kt`
- After all-clear message:
  `preSosId = getPreSosPrimaryId() ?: DefaultActiveContour.ID` →
  `setPrimaryContour(preSosId)` → `setSosMode(false)` → `savePreSosPrimaryId(null)`
- Add `SetPrimaryContourUseCase` to constructor
- Replace `setEmergencyActive(false)` with sequence above

#### Step 3.13 — `ContourItem` (presentation model)
File: `presentation/feature/settings/models/ContourItem.kt`
- Add `isPrimary: Boolean`

#### Step 3.14 — `UserSettingsUiState`
File: `presentation/feature/settings/UserSettingsUiState.kt`
- Add `primaryContourId: ContourId?` (null = not yet loaded)

#### Step 3.15 — `UserSettingsViewModel`
File: `presentation/feature/settings/UserSettingsViewModel.kt`
- Add `SetPrimaryContourUseCase` to constructor
- Subscribe to `observePrimaryContourId()` in combine; map to `ContourItem.isPrimary`
- Add `onSetPrimary(id: ContourId)` → `viewModelScope.launch { setPrimary(id, isConnected) }`
- Replace `onToggleActive(id, isActive)` guard: disable not available for Emergency or Primary
  (ViewModel validates before calling use case; use case also guards)
- Update `onToggleActive` for Emergency: no-op (Emergency toggle removed from UI but keep safe)

#### Step 3.16 — `UserTabContent`
File: `presentation/feature/settings/user/UserTabContent.kt`
- Replace `isActive` Switch/Checkbox with per-contour DropdownMenu trigger icon
- Add `RadioButton` or Primary badge (per Phase 2 design)
- Dropdown items mapped per contour type (see Phase 2 spec)
- Pass `onSetPrimary` from ViewModel to composable

#### Step 3.16b — `NodeProvisioningUseCase`
File: `domain/mesh/usecase/NodeProvisioningUseCase.kt`
- Pre-seed `usedSlots = mutableSetOf(0, 1)` (reserve slot 0 = primary, slot 1 = Emergency)
- Primary contour is already written to slot 0 by `SyncContoursOnConnectUseCase`; provision
  handles only non-primary non-emergency contours to slots 2–7

#### Step 3.17 — DI
File: `di/UserSettingsModule.kt`
- Add: `single { SetPrimaryContourUseCase(get(), get()) }`
- Add: `single { ActivateExclusiveContourUseCase(get(), get()) }`
- Update `SyncContoursOnConnectUseCase` binding (new param count)
- Update `TriggerEmergencyUseCase` binding (new params)
- Update `CancelEmergencyUseCase` binding (new params)

#### Step 3.18 — `IngestReceivedGeoMarksUseCase` (routing fix)
File: `domain/marker/usecase/IngestReceivedGeoMarksUseCase.kt`

Current code incorrectly maps **slot 0 → Emergency**. After redesign: slot 0 = Primary, slot 1 = Emergency.

New routing:
```
packet.channel
  == 0 → primaryContourId (read from ContourRepository.getPrimaryContourId())
          find contour → isActive = true by Primary invariant → process
  == 1 → Emergency
          SOS active  → process (geo saved + displayed)
          SOS inactive → drop (geo from LongFast ignored outside SOS)
  == N → existing: slotToHash[N] → contour → takeIf { isActive }
```
Add `ContourRepository` to constructor if not already present.

#### Step 3.19 — `MeshNodeModel` + node slot tracking
File: `domain/mesh/model/MeshNodeModel.kt`
```kotlin
data class MeshNodeModel(
    // ... existing fields ...
    val receivedOnSlot: Int? = null,
)
```

**Implementation note — source of `receivedOnSlot`:**
`NodeMapper.toMeshNodeModel()` maps from `Node` (BLE library model), which does **not** carry packet channel info. The channel is available only in `MeshPacket.channel` at receive time.

Required additional infrastructure in `MeshNetworkRepositoryImpl` (or new helper):
- Subscribe to incoming position `MeshPacket` stream from mesh layer
- Maintain `Map<Int, Int>` (`nodeNum → lastPositionSlot`)
- When building `MeshNodeModel`: look up `lastPositionSlot[node.num]`

Confirm with `/architect` whether the mesh layer exposes a raw packet stream or if a hook in the existing position-packet processing path is needed.

#### Step 3.20 — `ObserveNodeMarkersUseCase` (contour filter)
File: `domain/map/usecase/ObserveNodeMarkersUseCase.kt`

Add to constructor: `ContourRepository`, `ChannelSlotResolver`.

Filter nodes after existing age/validity checks:
```
node.receivedOnSlot
  == null → show (fallback until Step 3.19 fully implemented)
  == 0    → show (Primary, always active)
  == 1    → show only if sosMode == true
  == N    → slotToHash[N] → find contour → show if isActive; else hide
```
Combine with `observeContours()` + `observeSosMode()` reactive flows so filter re-evaluates on contour activation change.

#### Step 3.21 — `ObserveGeoNodesUseCase` (contour filter)
File: `domain/mesh/usecase/ObserveGeoNodesUseCase.kt`

Same filter logic as Step 3.20. Add same constructor deps.

#### Step 3.22 — `MeshToChatAdapter` — Emergency silent mode
⚠️ **DEFERRED** — Incoming message unread counter increment happens in `mesh/PacketRepositoryImpl` (mesh library layer), not in `MeshToChatAdapter`. `MeshToChatAdapter` only observes already-stored messages. Modifying the mesh library for SOS gating requires a separate task. The message is still stored (correct per spec); only unread counter and notification suppression are missing.

#### Step 3.23 — DI updates for isolation steps
File: `di/UserSettingsModule.kt` (or relevant DI modules)
- `ObserveNodeMarkersUseCase`: add `ContourRepository`, `ChannelSlotResolver` to binding
- `ObserveGeoNodesUseCase`: same
- `MeshToChatAdapter`: add `ContourRepository` to binding
- `IngestReceivedGeoMarksUseCase`: add `ContourRepository` to binding if not present

After all steps: `/simplify` on changed files.

---

### Phase 3.5 — Pre-test: изоляция Emergency-канала

**Цель:** предотвратить спам в публичную LongFast-сеть при ручном SOS-тестировании на реальных девайсах.

**⚠️ ОБЯЗАТЕЛЬНЫЙ ШАГ перед любым ручным тестом. Phase 4 не начинается без выполнения этого шага.**

**Действие:** временно изменить константы в `DefaultContour.kt`:

```kotlin
// ВРЕМЕННО — только для тестирования, НЕ КОММИТИТЬ
const val CHANNEL_NAME = "MTTestSOS"   // вместо "LongFast"
const val OPEN_PSK     = "<согласованный тестовый PSK base64>"  // вместо "AQ=="
```

Тестовый PSK согласовывается командой заранее и одинаков на всех тестовых девайсах.
Собрать и установить APK на все тестовые устройства.

**Проверка перед тестом:**
- [ ] `DefaultContour.CHANNEL_NAME != "LongFast"` (не дефолт)
- [ ] Все тестовые устройства установили одинаковый APK
- [ ] `git diff DefaultContour.kt` показывает изменённые константы (не закоммичено)

---

### Phase 4 — Testing
**Goal:** Green test suite; new use cases covered.
**Tasks:**
- Fix broken tests caused by `ContourRepository` interface change (update `FakeContourRepository`
  callers in existing test classes)
- `SetPrimaryContourUseCase` — unit test: saves DataStore; writes channel when `isConnected=true`;
  skips write when `isConnected=false`; handles Emergency id
- `ActivateExclusiveContourUseCase` — unit test: sets Primary; deactivates all non-emergency;
  writes slots 0+1; clears slots 2–7; does not touch Emergency isActive
- `TriggerEmergencyUseCase` (revised) — unit test: saves preSosPrimary; sets SOS mode; sets
  Emergency as Primary; starts broadcast
- `CancelEmergencyUseCase` (revised) — unit test: restores preSosPrimary; clears SOS mode;
  stops broadcast
- `SetContourActiveUseCase` (revised) — unit test: Emergency id → no-op; Primary id + deactivate
  → no-op; regular id → updates DB
- `SyncContoursOnConnectUseCase` (revised) — unit test: slot 0 = primary; slot 1 = Emergency;
  free slots start from 2; SOS active → Emergency on slot 0, others from slot 1

**Isolation (new):**
- `GeoSendPolicyImpl` — unit test: `observeAllowed()` emits `true` always (not gated by SOS flag)
- `IngestReceivedGeoMarksUseCase` (routing fix) — unit test:
  - slot 0 packet → resolves to primaryContour → geo accepted
  - slot 1 + SOS inactive → geo dropped
  - slot 1 + SOS active → geo accepted
  - slot N + inactive contour → dropped
- `ObserveNodeMarkersUseCase` — unit test:
  - node with `receivedOnSlot=1`, SOS inactive → excluded
  - node with `receivedOnSlot=1`, SOS active → included
  - node with `receivedOnSlot=N`, contour inactive → excluded
  - node with `receivedOnSlot=null` → included (fallback)
- `ObserveGeoNodesUseCase` — same filter cases
- `MeshToChatAdapter` silent mode — unit test:
  - slot 1 message + SOS inactive → stored, no notification, no unread increment
  - slot 1 message + SOS active → stored + notification + unread increment

**Skill:** `/tester` for scaffolding; direct coding for implementation
**Output:** passing test suite

---

### Phase 4.5 — Post-test: восстановление дефолтных значений

**Цель:** гарантировать, что production-код использует официальные Emergency-константы.

**⚠️ ОБЯЗАТЕЛЬНЫЙ ШАГ. Тестировщик подтверждает корректную работу → выполняется этот шаг → только тогда Phase 5.**

**Условие перехода:** тестировщик явно подтвердил:
- [ ] SOS-активация работает корректно
- [ ] Гео-трансляция работает корректно
- [ ] Отмена SOS работает корректно
- [ ] Silent-режим Emergency вне SOS работает корректно

**Действие:** вернуть константы в `DefaultContour.kt` к дефолту:

```kotlin
// ВОССТАНОВИТЬ дефолтные значения
const val CHANNEL_NAME = "LongFast"
const val OPEN_PSK     = "AQ=="
```

**Проверка:**
- [ ] `git diff DefaultContour.kt` — файл не изменён относительно исходного состояния (или показывает только запланированные изменения, не тестовые константы)
- [ ] `grep "MTTestSOS" DefaultContour.kt` — пусто (тестовые значения не остались)

**План не может быть закрыт без прохождения этого шага.**

---

### Phase 5 — Integration Review
**Goal:** No Clean Architecture violations.
**Tasks:** Check that new use cases don't import data-layer classes; `ActivateExclusiveContourUseCase`
stays in domain; presentation doesn't call `ContourRepository` directly.
**Skill:** direct review
**Output:** violations fixed (none expected)

---

### Phase 6 — Skill Update Review
**Tasks per skill:**
- `/architect`: add note on slot reservation pattern (`usedSlots` pre-seeding); Primary mechanic
  as DataStore single-source-of-truth pattern
- `/ui-designer`: record ContourItem dropdown pattern; Primary radio-button visual decision
- `/icon-designer`: no changes
- `/planner`: no methodology gaps found
**Output:** updated skill files or explicit "no changes" per skill

---

### Phase 6b — Project Docs & Memory Update
**Tasks:**
- `CLAUDE.md` status table: "Контуры (CRUD + isActive + Geo Protection)" → already Done; add
  note "Primary mechanic + Exclusive mode: In Progress"
- `.claude/docs/contours.md`: already updated (is the source of this plan)
- Archive this plan: `.claude/plans/contours-redesign.md` → `.claude/archive/contours-redesign.md`
- Memory: update `project_state.md` — contours feature in progress

---

### Phase 7 — Commit Preparation
**Предусловие:** `grep "MTTestSOS" DefaultContour.kt` — пусто. Тестовые константы не попадают в коммит.

**Tasks:** `git status` → stage by name → propose Russian commit message → wait for confirmation.
Suggested message:
```
feat(contours): Primary-контур, SOS-отвязка, изоляция контуров
```

---

## Coordination Map

```
Phase 0:   skip
Phase 1:   direct code review (call-site audit) → [/compact]
Phase 2:   /ui-designer component: ContourItem card + dropdown
Phase 3:   direct coding in order 3.1→3.23 → /simplify
Phase 3.5: ⚠️ временная замена DefaultContour-констант → сборка → установка на тест-девайсы
Phase 4:   /tester scaffolding + direct coding (unit tests)
Phase 4.5: ⚠️ тестировщик подтверждает OK → восстановить DefaultContour-константы → проверить git diff
Phase 5:   direct review (Clean Architecture)
Phase 6:   direct skill updates
Phase 6b:  docs & memory
Phase 7:   git stage → commit message → confirmation → git commit
```

**Жёсткие гейты:**
- Phase 4 не начинается без Phase 3.5 (тест-константы установлены)
- Phase 5 не начинается без Phase 4.5 (константы восстановлены, тестировщик ОК)
- Phase 7 не выполняется если `DefaultContour.kt` содержит тестовые значения

---

## Open Questions

~~1. `TriggerEmergencyUseCase` connection awareness~~ — **Resolved.** `writeChannel` has own
`myNodeNum ?: return` guard. `isConnected` param not needed anywhere.

~~2. Clearing a slot via `writeChannel(i, "", "")`~~ — **Resolved.** Empty PSK → `ByteArray(0)` →
`isEnabled = false`. Safe and correct.

~~3. SOS active on startup~~ — **Resolved.** `primary_contour_id` persisted in DataStore. No
extra init logic needed.

4. **`NodeProvisioningUseCase` primary handling** — After redesign, `NodeProvisioningUseCase`
skips Emergency (existing) but does NOT skip Primary explicitly. Need to add skip for primary
contour OR accept that it may land on slot 2+ if slot 0 is already occupied by correct hash.
Preferred: add skip `if (contour.id == primaryId) return@forEach` after reading primaryId from
DataStore. Resolve in Step 3.16b.

---

## Change Log

- 2026-05-29: created
- 2026-05-29: Phase 1 complete — call-site audit done; `isConnected` param removed; `NodeProvisioningUseCase` added to scope; slot-clearing approach confirmed
- 2026-06-01: expanded scope — isolation guarantees: node map filter, Emergency silent mode, IngestReceivedGeoMarksUseCase routing fix, GeoSendPolicy bug correction; Steps 3.18–3.23 added; Phase 4 extended
- 2026-06-01: заменён Step 3.24 (runtime debug override) на процессные гейты Phase 3.5 / Phase 4.5 — временная замена DefaultContour-констант перед тестом, обязательный возврат после подтверждения тестировщика
- 2026-06-01: Phase 3 завершена (сборка OK). Исправлены баги: 3.4 GeoSendPolicyImpl (flowOf(true)), 3.18 IngestReceivedGeoMarksUseCase (slot 0→primary, slot 1→Emergency+SOS gate). Добавлены: 3.19 MeshNodeModel.receivedOnSlot, 3.20/3.21 контур-фильтры в ObserveNodeMarkersUseCase/ObserveGeoNodesUseCase. Step 3.22 deferred (unread gate в mesh-библиотеке, вне scope).
