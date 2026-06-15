# Plan: Emergency SOS

**Date**: 2026-04-26
**Status**: Draft

## Summary

The Emergency SOS feature adds a global `emergencyMode` flag to the app. In the contour list, the Emergency contour card gets an `ic_sos` button instead of a checkbox. When SOS is inactive the button is red; pressing it triggers a confirmation dialog, sends a distress message with coordinates to the Emergency channel, and starts continuous GPS broadcast to that channel. When SOS is active the card turns red and the button turns grey; pressing it shows a "Cancel alarm?" dialog, sends an all-clear message, and stops the position broadcast.

## Scope

- In scope:
  - Global `emergencyMode` flag (persisted, survives app restart)
  - Emergency contour card UI: SOS button replaces checkbox, red card overlay when active
  - Two confirmation dialogs (trigger / cancel)
  - Send distress message `"{Callsign} просит помощи, координаты: {lat}, {lon}"` to Emergency channel
  - Send all-clear message `"Пользователь {Callsign} отметил, что с ним всё в порядке, помощь не требуется"` to Emergency channel
  - Start / stop continuous position broadcast to Emergency channel while `emergencyMode = true`
  - Two toasts: "Запрос о помощи и координаты отправлены" / "В экстренный канал начата трансляция геопозиции"
- Out of scope:
  - Emergency notification on the *receiving* side (other nodes)
  - Badge or HUD indicator for active emergencyMode (deferred — add to HUD plan)
  - Behavior when no Emergency contour exists or has no channel assigned

## Architecture Notes

### Global emergencyMode flag

`ContourRepository` already has `observeEmergencyIsActive(): Flow<Boolean>` and `setEmergencyActive(isActive: Boolean)` backed by DataStore key `"emergency_is_active"`. This IS the `emergencyMode` flag — no new storage needed.

New wrapper use cases expose it cleanly:
- `ObserveEmergencyModeUseCase` → `ContourRepository.observeEmergencyIsActive()`
- `SetEmergencyModeUseCase(isActive: Boolean)` → `ContourRepository.setEmergencyActive(isActive)`

### Distress message sending

`SendChatMessageUseCase(params: SendChatMessageParams { text, contactId, channel: Int })` already sends to any channel. The Emergency contour's channel index must be resolved from `Contour.transport` (the channel hash/slot). A new use case resolves this:

```
SendEmergencyMessageUseCase
  1. observeUser() → callsign
  2. get emergency contour → resolve channel index
  3. gpsRepository.location.value → coordinates
  4. SendChatMessageUseCase(text, contactId="^all", channel=emergencyChannelIndex)
```

### Continuous position broadcast

While `emergencyMode = true`, the app must periodically send position GeoMarks to the Emergency channel. This is a background concern that survives screen changes → implemented as a `Flow`-based coroutine launched in a **repository-level scope** (not ViewModel):

```
EmergencyPositionBroadcastRepository
  - start(): launches coroutine that combines gps.location + 30s ticker → sends GeoMark to emergency channel
  - stop(): cancels the coroutine
  - isActive: StateFlow<Boolean>
```

Lifecycle: `start()` / `stop()` called from `TriggerEmergencyUseCase` / `CancelEmergencyUseCase`. The repository itself holds the `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.

### New domain use cases

| Use case | Layer | Description |
|---|---|---|
| `ObserveEmergencyModeUseCase` | domain | Wraps `ContourRepository.observeEmergencyIsActive()` |
| `SetEmergencyModeUseCase` | domain | Wraps `ContourRepository.setEmergencyActive()` |
| `TriggerEmergencyUseCase` | domain | Sets flag, sends distress message, starts broadcast |
| `CancelEmergencyUseCase` | domain | Sends all-clear, stops broadcast, clears flag |
| `ResolveEmergencyChannelIndexUseCase` | domain | Looks up emergency Contour → returns channel Int |

### Presentation

`ContourItem` (presentation model) already has `isEmergency: Boolean`. The `ContourCard` composable must branch:
- `isEmergency == false` → existing checkbox UI
- `isEmergency == true` → `EmergencyContourCard` composable
  - Collects `emergencyMode` from `UserSettingsViewModel`
  - Renders `MeshIconButton(ic_sos)` colored red (inactive) or grey (active)
  - Card background: `MaterialTheme.colorScheme.surface` (inactive) or red overlay (active)

Dialogs: stateless composables `TriggerEmergencyDialog` / `CancelEmergencyDialog`, shown via `Boolean` flag in ViewModel state.

## Phase Plan

### Phase 0 — Research
*Skip* — domain is well understood from code exploration.

---

### Phase 1 — Architecture Design

**Goal**: Approved architecture for all layers — domain interfaces, data implementations, DI wiring, ViewModel state shape.

**Tasks**:
1. Design `EmergencyPositionBroadcastRepository` interface + impl skeleton
2. Define all 5 new use case signatures
3. Define ViewModel state additions to `UserSettingsUiState`: `emergencyMode: Boolean`, `showTriggerDialog: Boolean`, `showCancelDialog: Boolean`
4. Verify `ResolveEmergencyChannelIndexUseCase` — confirm Contour model has accessible channel index field; decide if it wraps `ContourRepository.findByChannelHash()` or reads `Contour.transport` directly
5. Decide scope for `EmergencyPositionBroadcastRepository`: Koin `single` in app module, initialized at DI startup

**Skill**: `/architect feature: Emergency SOS — see .claude/plans/emergency-sos.md for full architecture notes`
**Output**: Architecture plan section updated with final decisions

> **Token checkpoint**: run `/compact` before Phase 2.

---

### Phase 2 — UI Design

**Goal**: Approved component design for `EmergencyContourCard`, dialogs, and SOS button states.

**Tasks**:
1. Design `EmergencyContourCard`:
   - `emergencyMode=false`: standard card surface, `ic_sos` button with `ErrorContainer` or `Error` color fill
   - `emergencyMode=true`: card with red/error background overlay (e.g., `MaterialTheme.colorScheme.errorContainer`), button with `SurfaceVariant` (disabled-looking grey)
2. Design `TriggerEmergencyDialog` — title "Позвать на помощь?", confirm "ДА" (error color), dismiss "Отмена"
3. Design `CancelEmergencyDialog` — title "Всё хорошо? Отменить тревогу?", confirm "Да", dismiss "Нет"
4. `ic_sos.xml` is already added — verify it renders correctly in `MeshIconButton` context

**Skill**: `/ui-designer component: EmergencyContourCard and SOS dialogs`
**Output**: Component spec, color token decisions

---

### Phase 3 — Implementation

**Goal**: Working feature across all layers.

**Order**:
1. **Domain**: `ResolveEmergencyChannelIndexUseCase`, `ObserveEmergencyModeUseCase`, `SetEmergencyModeUseCase`
2. **Domain**: `TriggerEmergencyUseCase`, `CancelEmergencyUseCase`
3. **Data**: `EmergencyPositionBroadcastRepository` interface + `EmergencyPositionBroadcastRepositoryImpl`; on init reads persisted `emergencyMode` flag and calls `start()` if true
4. **DI**: wire all new use cases + repository in Koin modules; `EmergencyPositionBroadcastRepositoryImpl` as `single` with `createdAtStart = true`
5. **Presentation**: extend `UserSettingsUiState` — add `emergencyMode: Boolean`, `isNodeConnected: Boolean`, `showTriggerDialog: Boolean`, `showCancelDialog: Boolean`; collect `MeshConnectionRepository` stream in `UserSettingsViewModel`
6. **Presentation**: `EmergencyContourCard` — button disabled when `!isNodeConnected`, red when `!emergencyMode && isNodeConnected`, grey when `emergencyMode`
7. **Presentation**: `TriggerEmergencyDialog`, `CancelEmergencyDialog`
8. **Presentation**: hook up ViewModel → card → dialogs → toasts

**Skill**: Direct coding (EnterPlanMode before starting)
**After**: run `/simplify` on changed files
**Output**: Buildable, working feature

---

### Phase 4 — Testing

**Goal**: Feature verified at unit + integration level.

**Tasks**:
1. Unit: `TriggerEmergencyUseCase` — verify message sent with correct text, flag set, broadcast started
2. Unit: `CancelEmergencyUseCase` — verify all-clear message sent, broadcast stopped, flag cleared
3. Unit: `UserSettingsViewModel` — dialog open/close state, emergencyMode reflected in UI state
4. Manual: smoke test on device — trigger → confirm toast appears → map shows position marks → cancel → marks stop

**Skill**: `/tester feature: Emergency SOS use cases and ViewModel`
**Output**: Passing test suite

---

### Phase 5 — Integration Review

**Goal**: No Clean Architecture violations.

**Tasks**:
1. Verify `EmergencyPositionBroadcastRepository` scope does not leak presentation concerns
2. Verify use cases do not import Android framework types
3. Check DI module organization

**Skill**: `/architect review: emergency SOS changed files`
**Output**: Review sign-off

---

### Phase 6 — Skill Update Review

*(Always — even if no changes needed)*

- `/architect`: new `RepositoryScope` pattern for background coroutine repositories → document if novel
- `/ui-designer`: `ErrorContainer` usage for active-alert card backgrounds → add to color token decisions if not already there
- `/icon-designer`: no new icon style decisions (ic_sos already designed)
- `/planner`: no methodology gaps identified

---

### Phase 6b — Docs & Memory Update

- Update CLAUDE.md: add "Emergency SOS" row with status
- Create `.claude/docs/emergency-sos.md`
- Archive this plan → `.claude/archive/emergency-sos.md`
- Update `memory/project_state.md`

---

### Phase 7 — Commit Preparation

- Stage changed files by name
- Draft commit message in Russian
- Wait for user confirmation before `git commit`

## Coordination Map

```
Phase 0: [skip]
Phase 1: /architect feature: Emergency SOS → [/compact]
Phase 2: /ui-designer component: EmergencyContourCard + dialogs
Phase 3: [direct coding] → /simplify
Phase 4: /tester → [direct coding — tests]
Phase 5: /architect review: emergency SOS files
Phase 6: [skill update review]
Phase 6b: [CLAUDE.md + .claude/docs/emergency-sos.md + archive + memory/]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Resolved Decisions

1. **Broadcast frequency**: 30 seconds. ✅
2. **App restart resilience**: If `emergencyMode = true` persists in DataStore, broadcast resumes automatically on startup. `EmergencyPositionBroadcastRepository` must be initialized at app start (Koin `single`, eagerly created) and re-check the persisted flag. ✅
3. **No channel on contour**: Not possible — `Contour.transport` is non-nullable. However, the SOS button must be **disabled when no node is connected**. Condition: `isNodeConnected && !emergencyMode` → button enabled (red); `emergencyMode` → button enabled (grey/cancel); `!isNodeConnected` → button disabled regardless. `UserSettingsViewModel` must collect `isNodeConnected` from `MeshConnectionRepository` and expose it in `UserSettingsUiState`. ✅
4. **Callsign empty**: Fall back to node short name from the local node's `User.shortName`. `TriggerEmergencyUseCase` / `CancelEmergencyUseCase` resolve: `displayName.ifBlank { localNode.user.shortName }`. ✅

## Open Questions

1. **Channel index resolution**: Does `Contour` have a direct `channelIndex: Int` field, or must it be resolved via `slotToHash` map in `ChannelSlotResolver`? Affects `ResolveEmergencyChannelIndexUseCase` implementation — clarify in Phase 1.
2. **Contactid for Emergency broadcast**: Is `"^all"` the correct `contactId` for sending to an open channel broadcast? Confirm from `meshtastic-contacts-channels.md` in Phase 1.

## Change Log

- 2026-04-26: created
- 2026-04-26: resolved decisions 1–4 (broadcast frequency, restart resilience, channel nullability, callsign fallback)
