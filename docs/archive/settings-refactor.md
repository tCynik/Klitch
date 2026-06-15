# Plan: Settings Screen Refactor

**Date**: 2026-04-11
**Status**: Approved

## Summary

The `settings_screen` branch introduced a working marker-size settings screen but left several issues: ViewModels depend directly on the data layer (`AppSettings`), the size formula is duplicated, and test coverage is missing. This plan cleans all of that up before the branch is merged into `main`.

## Scope

- **In scope**: introduce a domain interface for marker settings; remove data-layer imports from ViewModels; deduplicate the size formula; extract `SettingsTab` enum; localise tab labels and snackbar string; write unit tests for `SettingsViewModel` and `MarkerSizeConfig`.
- **Out of scope**: new settings categories, design-system tokens, any changes to map rendering or BLE logic.

## Issues from Review

| Priority | Issue |
|---|---|
| High | `MainViewModel` and `SettingsViewModel` import `data.settings.AppSettings` directly — violates Clean Architecture |
| Medium | Size formula `24 + (level - 1) * 6` duplicated in `SettingsScreen` and `MarkerSizeConfig.fromLevel()` |
| Medium | No tests for `SettingsViewModel` or `MarkerSizeConfig.fromLevel()` |
| Low | `getMarkerSizeLevelInternal()` is a private duplicate of `getMarkerSizeLevel()` in `AppSettings` |
| Low | Tab labels are in English; snackbar string `"Сохранено"` is hardcoded |
| Low | `SettingsTab` enum lives in `SettingsScreen.kt` — should be its own file |

## Step-by-Step Plan

### Step 1 — Introduce domain interface

Create `domain/settings/repository/MarkerSettingsRepository.kt`:

```kotlin
interface MarkerSettingsRepository {
    val markerSizeLevelFlow: StateFlow<Int>
    fun getMarkerSizeLevel(): Int
    fun setMarkerSizeLevel(level: Int)
}
```

### Step 2 — `AppSettings` implements `MarkerSettingsRepository`

File: `shared/src/commonMain/.../data/settings/AppSettings.kt`

- Add `implements MarkerSettingsRepository`
- Remove `getMarkerSizeLevelInternal()`; replace its use in the `_markerSizeLevel` initialiser with `getMarkerSizeLevel()`

### Step 3 — Wire DI

Files: `di/PresentationModule.kt` (and/or shared DI module)

- Bind `AppSettings` as `MarkerSettingsRepository`
- `MainViewModel` and `SettingsViewModel` receive `MarkerSettingsRepository`

### Step 4 — Update ViewModels

Files: `SettingsViewModel.kt`, `MainViewModel.kt`

- Replace dependency type `AppSettings` → `MarkerSettingsRepository`
- Remove `import ru.tcynik.meshtactics.data.settings.AppSettings`

### Step 5 — Deduplicate size formula

File: `SettingsScreen.kt:105`

Replace inline formula with `MarkerSizeConfig.fromLevel(markerSizeLevel).value.toInt()`.

### Step 6 — Extract `SettingsTab` enum

Create `presentation/feature/settings/SettingsTab.kt`, move enum there, remove from `SettingsScreen.kt`.

### Step 7 — Localise strings

Files: `SettingsScreen.kt`, `res/values/strings.xml`

- Translate tab labels to Russian
- Move `"Сохранено"` to string resources

### Step 8 — Tests: `SettingsViewModelTest`

File: `app/src/test/.../presentation/feature/settings/SettingsViewModelTest.kt`

Cases:
- `onMarkerSizeLevelChange` updates only `pending`, not `markerSizeLevel`
- `onSave()` commits pending and calls `repository.setMarkerSizeLevel()`
- Initialisation reads the value from the repository

### Step 9 — Tests: `MarkerSizeConfigTest`

File: `app/src/test/.../presentation/feature/main/osd/models/MarkerSizeConfigTest.kt`

Cases: level=1 → 24dp, level=10 → 78dp, level=5 → 48dp (default), level=6 → step +6dp.

## Execution Order

```
Step 1 → Step 2 → Step 3 → Step 4   ← architectural chain (strictly sequential)
Step 5 → Step 6 → Step 7            ← UI cleanup (can run in parallel with tests)
Step 8 → Step 9                     ← tests (after Step 4)
```

## Definition of Done

- `grep -r "data.settings" presentation/` returns 0 results
- `./gradlew test` passes
- `MarkerSizeConfig.fromLevel()` tested at boundaries
- `SettingsViewModel` covered by three test cases

## Change Log

- 2026-04-11: created
