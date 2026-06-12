# Plan: Callsign Gate on Connect

**Date**: 2026-05-26
**Status**: Done

## Summary

Block BLE node connection if the user has no callsign (displayName) set. The app remains fully usable offline; only the connect action is gated. Follows the existing HUD + MeshTestScreen dialog pattern used for sync-required state: HUD INFO shows "установите позывной" when callsign is blank, and navigating to the network screen auto-shows `CallsignGateDialog`. After entering a callsign and confirming, the callsign is saved and the connection proceeds normally (→ sync → reboot). Auto-connect on app restart is skipped silently if callsign is blank. Additionally, `UserSettingsViewModel.onSaveAndReboot()` is guarded against writing a blank owner name.

## Scope

- **In scope**:
  - Gate on `onConnectClick()` in `MeshTestViewModel`
  - Gate on auto-connect in `MainViewModel` — skip silently if `displayName` is blank
  - `CallsignGateDialog` composable with inline TextField + Confirm/Cancel
  - `onCallsignConfirmed()`: `saveAppUser` → `connectToDevice` → clear dialog state
  - Guard in `UserSettingsViewModel.onSaveAndReboot()`: blank → error state, no write
- **Out of scope**:
  - Uniqueness check across the mesh network
  - Blocking overlay on the main map screen
  - Auto-navigation to UserSettings
  - Any gate on offline features (map, geo marks, markers)

## Architecture Notes

**Pattern**: mirrors the existing sync-required flow. `MainViewModel` tracks the condition and surfaces it in HUD INFO. `MeshTestScreen` auto-shows the dialog on enter when condition is active.

**HUD INFO** (`buildConnectionInfoSlot()`, line ~996 in `MainViewModel`): add case only in the `Scanning` branch — when `callsignRequired` → `HudInfoSlot(content = "установите позывной", color = Color.Red)` (before "выбор узла" and "Поиск..."). `Disconnected` idle state: no change, remains `emptyInfoSlot()`. New field `callsignRequired: Boolean` in `MainUiState`. `MainViewModel` observes `AppUserRepository` via `ObserveAppUserUseCase` → sets `callsignRequired = displayName.isBlank()`.

**Auto-connect gate**: `MainViewModel` auto-connect path reads `displayName` before `connectToDevice`. If blank → skip (silent, no dialog). App stays Disconnected; HUD shows "установите позывной".

**MeshTestScreen dialog**: follows `SyncRequiredDialog` pattern. `MeshTestViewModel` on init checks `displayName` via `ObserveAppUserUseCase().first()` → if blank → immediately set `callsignGateDialog` state. `MeshTestScreen` shows `CallsignGateDialog` when `state.callsignGateDialog != null` — placed at top of composable, same as `SyncRequiredDialog`.

**Gate on `onConnectClick()`**: secondary/defensive — if user somehow dismisses dialog then taps Connect, show dialog again.

**Dialog state**: `CallsignGateDialogState(pendingAddress: String, pendingDeviceName: String, callsignInput: String)` in `MeshTestUiState`. Nullable = closed.

**Confirm path**: `onCallsignConfirmed()` — `saveAppUser(AppUser(callsignInput))` → `connectToDevice(pendingAddress, pendingDeviceName)` → clear dialog.

**DI**: Both `MeshTestViewModel` and `MainViewModel` manually wired in `PresentationModule` → add `observeAppUser = get()` + `saveAppUser = get()` to `MeshTestViewModel`; `observeAppUser = get()` to `MainViewModel`.

**UserSettings guard**: `onSaveAndReboot()` line 296 — add `if (displayName.isBlank()) { _uiState.update { it.copy(displayNameError = true) }; return@launch }`. Need `displayNameError: Boolean` in `UserSettingsUiState` + error indicator in `UserTabContent`.

## Phase Plan

### Phase 1 — State model
**Goal**: New state fields added
**Tasks**:
- Define `CallsignGateDialogState(pendingAddress: String, pendingDeviceName: String, callsignInput: String)` — new file in `meshtest/state/models/`
- Add `callsignGateDialog: CallsignGateDialogState? = null` to `MeshTestUiState`
- Add `callsignRequired: Boolean = false` to `MainUiState`
- Add `displayNameError: Boolean = false` to `UserSettingsUiState`

### Phase 2 — ViewModel changes
**Goal**: All three ViewModels updated with gate logic
**Tasks**:
- `MainViewModel`: inject `ObserveAppUserUseCase`; observe `displayName` → `callsignRequired = displayName.isBlank()`; in auto-connect path check `callsignRequired` → skip if true; in `buildConnectionInfoSlot()` add to `Scanning` branch (highest priority): if `callsignRequired` → `HudInfoSlot("установите позывной", Color.Red)` — `Disconnected` idle untouched
- `MeshTestViewModel`: inject `ObserveAppUserUseCase` + `SaveAppUserUseCase`; in `init` read `displayName` → if blank set `callsignGateDialog` with empty pendingAddress (screen-entry auto-show, no pending device yet)
- `MeshTestViewModel.onConnectClick()`: check `displayName.isBlank()` → if blank, set `callsignGateDialog` with pendingAddress/deviceName; else proceed as before
- Add `onCallsignInput(text: String)` — updates `callsignGateDialog.callsignInput`
- Add `onCallsignConfirmed()` — `saveAppUser` → if `pendingAddress.isNotBlank()` → `connectToDevice`; clear dialog
- Add `onCallsignDismissed()` — clear dialog, no connect
- `UserSettingsViewModel.onSaveAndReboot()`: guard `displayName.isBlank()` → `displayNameError = true`, return early

### Phase 3 — DI
**Goal**: Koin module compiles
**Tasks**:
- `PresentationModule.kt`: add `observeAppUser = get()` + `saveAppUser = get()` to `MeshTestViewModel` binding
- `PresentationModule.kt`: add `observeAppUser = get()` to `MainViewModel` binding

### Phase 4 — UI
**Goal**: Dialog composable wired in screen
**Tasks**:
- Create `CallsignGateDialog.kt` in `meshtest/components/` — `OutlinedTextField` for callsign, "Подключить" button (disabled when `callsignInput.isBlank()`), "Отмена" button
- `MeshTestScreen.kt`: show `CallsignGateDialog` when `state.callsignGateDialog != null` (same position as `SyncRequiredDialog`), wire all three handlers
- `UserTabContent.kt`: show error indicator on TextField when `displayNameError = true`

### Phase 5 — Testing
**Goal**: Core logic verified
**Tasks**:
- `MeshTestViewModelCallsignGateTest`: blank displayName → dialog shown; non-blank → connect called directly; `onCallsignConfirmed` saves user + calls connect; `onCallsignDismissed` clears state without connect
- `MainViewModelCallsignTest`: blank displayName → `callsignRequired = true`; auto-connect skipped; HUD slot in `Scanning` state returns "установите позывной"; HUD slot in `Disconnected` state returns empty (no message); non-blank → `callsignRequired = false`, auto-connect proceeds
- `UserSettingsViewModelLeaveDialogTest`: `onSaveAndReboot()` with blank displayName → error state set, `writeOwner` NOT called

## Coordination Map

```
Phase 1: [state model — direct edit]
Phase 2: [ViewModel logic — direct coding]
Phase 3: [DI wiring — direct coding]
Phase 4: [UI composable + wiring — direct coding]
Phase 5: [tests — direct coding]
Phase 6: [skill update review]
Phase 6b: [docs & memory — CLAUDE.md, .claude/docs/callsign-gate-on-connect.md, archive plan]
Phase 7: [stage by name] → [propose commit message] → [wait for confirmation] → git commit
```

## Open Questions

- None — scope is fully determined.

## Edge Cases Addressed

| Сценарий | Поведение |
|---|---|
| Свежая установка, нет позывного, есть lastConnectedDevice | `MainViewModel`: авто-подключение пропускается. HUD: пусто (Disconnected idle). |
| Пользователь начинает скан без позывного | HUD в Scanning: "установите позывной" (красный). |
| Пользователь открывает экран сети без позывного | `MeshTestViewModel.init` → `CallsignGateDialog` появляется автоматически. |
| Пользователь вручную нажимает Connect без позывного (диалог закрыт) | `onConnectClick()` guard → `CallsignGateDialog` с `pendingAddress`. |
| DataStore сброшен между сессиями (позывной исчез) | То же что свежая установка. |
| Пустое поле в UserSettings → "Сохранить" | `onSaveAndReboot()` guard — `writeOwner` не вызывается, ошибка в UI. |

## Change Log

- 2026-05-26: created
