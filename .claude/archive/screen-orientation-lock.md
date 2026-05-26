# Plan: Screen Orientation Lock Setting

**Date**: 2026-05-26
**Status**: Done

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
fun setOrientationLocked(locked: Boolean)
fun getOrientationMode(): ScreenOrientationMode
fun setOrientationMode(mode: ScreenOrientationMode)
fun observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>>
```

Use cases (in `app/domain/settings/usecase/`):
- `GetScreenOrientationLockedUseCase`
- `SetScreenOrientationLockedUseCase`
- `GetScreenOrientationModeUseCase`
- `SetScreenOrientationModeUseCase`
- `ObserveScreenOrientationSettingsUseCase`

### Data layer (`shared/commonMain`)

`AppSettings` implements `ScreenOrientationRepository`.  
Keys: `"screen_orientation_locked"` (Boolean, default false), `"screen_orientation_mode"` (String, default `"SYSTEM"`).

### Presentation layer (`app`)

`SettingsUiState` adds orientation pending fields.  
`DisplaySettingsScreen`: Checkbox + AnimatedVisibility + ExposedDropdownMenuBox.  
`MainActivity`: collects orientation Flow → `requestedOrientation`.

### DI

`CommonModule` → `ScreenOrientationRepository`.  
`MapDataModule` → use cases.

## Phase Plan

All phases completed 2026-05-26.

## Open Questions

~~1. Should the dropdown use `ScreenOrientationMode.SYSTEM` as a third option?~~
**Resolved**: checkbox only — no third dropdown option.

~~2. Is `ExposedDropdownMenuBox` already used elsewhere?~~
**Resolved**: yes, GeoMarksSheet.

~~3. StateFlow merging for observeOrientationSettings~~
**Resolved**: `combine(_orientationLocked, _orientationMode)` in AppSettings.

## Change Log

- 2026-05-26: created
- 2026-05-26: implemented (phases 1–6b)
