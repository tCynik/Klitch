# Plan: Screen Orientation Lock Setting

**Date**: 2026-05-26
**Status**: Approved

## Summary

User can pin the app to a specific screen orientation (portrait or landscape) from the Display Settings screen. A checkbox enables locking; when checked, a dropdown appears with two choices. On Save the setting persists and is immediately applied to the Activity (auto-rotate disabled). When unchecked, auto-rotate is restored and the dropdown is hidden.

## Scope

- **In scope**: checkbox + dropdown in DisplaySettingsScreen, domain model + repo interface, AppSettings persistence, ViewModel pending state, apply orientation via MainActivity observer.
- **Out of scope**: per-screen orientation overrides, landscape-specific layout variants, dynamic mode changes outside settings.

## Architecture Notes

### Domain layer (`shared/commonMain`)

New enum:
```
ScreenOrientationMode { SYSTEM, PORTRAIT, LANDSCAPE }
```

New repository interface `ScreenOrientationRepository`:
```kotlin
fun getOrientationLocked(): Boolean
suspend fun setOrientationLocked(locked: Boolean)
fun getOrientationMode(): ScreenOrientationMode
suspend fun setOrientationMode(mode: ScreenOrientationMode)
fun observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>>
```

Use cases (in `shared/domain/settings/usecase/`):
- `GetScreenOrientationLockedUseCase`
- `SetScreenOrientationLockedUseCase`
- `GetScreenOrientationModeUseCase`
- `SetScreenOrientationModeUseCase`
- `ObserveScreenOrientationSettingsUseCase`

### Data layer (`shared/commonMain`)

`AppSettings` implements `ScreenOrientationRepository`.  
Keys: `"screen_orientation_locked"` (Boolean, default false), `"screen_orientation_mode"` (String, default `"SYSTEM"`).

### Presentation layer (`app`)

`SettingsUiState` adds:
```kotlin
val orientationLocked: Boolean = false
val orientationLockedPending: Boolean = false
val orientationModePending: ScreenOrientationMode = ScreenOrientationMode.SYSTEM
```

`SettingsViewModel` adds:
- `onOrientationLockedChange(locked: Boolean)` — updates pending
- `onOrientationModeChange(mode: ScreenOrientationMode)` — updates pending
- `onSave()` — already exists, add saving orientation settings here

`DisplaySettingsScreen` adds below existing controls:
1. `Row` with `Checkbox` + label "Закрепить ориентацию экрана"
2. `AnimatedVisibility(visible = orientationLockedPending)` wrapping an `ExposedDropdownMenuBox` with items "Вертикальная" / "Горизонтальная"

### Apply orientation (Android-only, `app` module)

`MainActivity.onCreate()` launches a coroutine that collects `ObserveScreenOrientationSettingsUseCase()` and calls:
```kotlin
requestedOrientation = when {
    !locked -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    mode == PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    mode == LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
```
This handles both "apply on start" and "apply immediately after save" — the Flow emits on every change.

### DI

`CommonModule` adds `ScreenOrientationRepository` binding (already implements in `AppSettings`).  
New use cases bound in `CommonModule` or a new `ScreenOrientationModule`.

## Phase Plan

### Phase 1 — Architecture (skill: /architect)
- **Goal**: domain model, repo interface, use case signatures, ViewModel state shape reviewed and approved
- **Tasks**: scaffold `ScreenOrientationMode`, `ScreenOrientationRepository`, use cases, AppSettings additions, DI bindings, ViewModel state additions
- **Output**: all new files created and wired, project builds

### Phase 2 — UI Implementation (direct coding)
- **Goal**: DisplaySettingsScreen shows checkbox + animated dropdown
- **Tasks**:
  - Add pending state fields to `SettingsUiState`
  - Add handlers to `SettingsViewModel`
  - Add UI block to `DisplaySettingsScreen` (Checkbox + AnimatedVisibility + ExposedDropdownMenuBox)
  - Wire ViewModel handlers to composable events
- **Output**: UI renders and responds to interaction

### Phase 3 — Apply Orientation (direct coding)
- **Goal**: setting persists and is applied to Activity immediately
- **Tasks**:
  - Extend `SettingsViewModel.onSave()` with orientation use cases
  - In `MainActivity.onCreate()`, launch `lifecycleScope.launch` to collect orientation settings flow and call `requestedOrientation`
- **Output**: orientation locks/unlocks without app restart

### Phase 4 — Testing (direct coding)
- **Goal**: ViewModel behavior verified
- **Tasks**:
  - Unit test: `onOrientationLockedChange` → `orientationLockedPending` updates
  - Unit test: `onSave()` calls `SetScreenOrientationLockedUseCase` + `SetScreenOrientationModeUseCase`
  - Manual smoke: enable lock → portrait → Save → rotate device → screen stays portrait; disable → auto-rotate resumes
- **Output**: passing unit tests + manual verification

### Phase 5 — Integration Review
- **Goal**: no Clean Architecture violations
- **Tasks**: verify `requestedOrientation` call stays in `app` module, domain stays platform-agnostic
- **Skill**: /architect review
- **Output**: review clean

### Phase 6 — Skill Update Review (always)
- `/architect`: new pattern — platform-specific side effects (Activity.requestedOrientation) applied via MainActivity Flow collector; domain stays KMP-clean
- `/ui-designer`: no new design system decisions (uses existing Checkbox + ExposedDropdownMenuBox patterns)
- `/icon-designer`: no changes
- `/planner`: no methodology gaps

### Phase 6b — Docs & Memory Update (always)
- Add row to CLAUDE.md status table
- Create `.claude/docs/screen-orientation-lock.md`
- Archive this plan to `.claude/archive/screen-orientation-lock.md`
- Update memory if needed

### Phase 7 — Commit Preparation (always)
- Stage files by name, propose commit message, wait for user confirmation

## Coordination Map

```
Phase 1: /architect feature: screen orientation lock → build check
Phase 2: direct coding (SettingsUiState, SettingsViewModel, DisplaySettingsScreen)
Phase 3: direct coding (SettingsViewModel.onSave, MainActivity orientation collector)
Phase 4: direct coding (unit tests + manual smoke)
Phase 5: /architect review: orientation lock changes
Phase 6: skill update review (architect only needs update)
Phase 6b: CLAUDE.md + .claude/docs/ + archive + memory
Phase 7: git stage → commit message → user confirmation → git commit
```

## Open Questions

~~1. Should the dropdown use `ScreenOrientationMode.SYSTEM` as a third option?~~
**Resolved**: checkbox only — no third dropdown option. Avoids duplication of toggle logic.

~~2. Is `ExposedDropdownMenuBox` already used elsewhere?~~
**Resolved**: yes, used in geo mark creation bottom sheet. Reuse that pattern directly.

3. `ObserveScreenOrientationSettingsUseCase` returns a Flow — verify `AppSettings` StateFlow merging approach matches existing `observeMarkerSizeLevel()` pattern.

## Change Log

- 2026-05-26: created
