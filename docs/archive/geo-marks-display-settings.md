# Plan: Geo Marks Display Settings

**Date**: 2026-05-20
**Status**: Done

## Summary

Adds two display settings for geo marks to `DisplaySettingsScreen`: a slider controlling the icon size of rendered point marks and draft marks on the map (`geoMarkSizeLevel`, 1–10), and a toggle that enables name labels next to point mark icons (`showGeoMarkNames`). Both settings are persisted via `AppSettings` and flow through to `MapLibreLayer` using the same repository/use-case/ViewModel pipeline as the existing `markerSizeLevel`.

## Scope

**In scope:**
- `geoMarkSizeLevel` slider (1–10, default 5) controls `iconSize` of `geo-draft-points`, `geo-received-points`, and `geo-received-track-anchors` SymbolLayers
- `showGeoMarkNames` toggle (default false) enables `textField` on `geo-received-points` SymbolLayer only (not drafts, not tracks)
- `name` field added to `buildReceivedPointsGeoJson` properties (prerequisite for text rendering)
- Settings persisted to `AppSettings` (`Settings` key-value store, same as marker size)

**Out of scope:**
- Name labels for track anchors or draft marks
- Font size or text style customization
- Label collision avoidance configuration

## Architecture Notes

### Existing pattern to replicate (`markerSizeLevel`)
- `MarkerSettingsRepository` interface (domain) — `StateFlow` + get/set
- `AppSettings` (data) — `Settings` key, `MutableStateFlow`, implements repository
- `GetXxxUseCase` + `ObserveXxxUseCase` — registered in `mapDataModule`
- `MainUiState.xxxLevel` — default 5
- `MainViewModel` — inject both use cases, init from get, observe for updates
- `MapLibreLayer` — receives level as param, computes `iconSize`
- `SettingsUiState.xxxPending` + `SettingsViewModel.onXxxChange()` + `onSave()`
- `DisplaySettingsScreen` — `Slider` or `Switch`, string resource for label

### Icon size formula
- Point marks (draft + received): `0.3f + (level - 1) * 0.05f` → range [0.30, 0.75]
- Track anchors: point size × 0.7 (maintain visual hierarchy)

### Name label rendering
- `buildReceivedPointsGeoJson` currently lacks `name` in properties → must add `"name":"${mark.name}"` (JSON-escape the string)
- `geo-received-points` SymbolLayer: when `showGeoMarkNames = true`, add:
  - `textField = feature["name"].asString()`
  - `textOffset = offset(0.0, 1.3)` (below icon)
  - `textHaloColor = const(Color.Black)`, `textHaloWidth = const(1.dp)`
  - `textAllowOverlap = const(false)` (avoid overlap; icons already use `allowOverlap = true`)

### DI placement
- New use cases: `mapDataModule` (same as `GetMarkerSizeLevelUseCase`, `ObserveMarkerSizeLevelUseCase`)
- `MarkerSettingsRepository` binding unchanged — `AppSettings` already implements it via `commonModule`

## Phase Plan

### Phase 0 — Research
**Skip** — all patterns are understood from existing code. No protocol or external API unknowns.

### Phase 1 — Architecture Design
**Skip** — architecture specified in full in this plan. Follows `markerSizeLevel` pattern exactly.

### Phase 2 — UI / Icon Design
**Skip** — no new icons; UI uses `Slider` + `Switch` (Material3), same as existing slider in `DisplaySettingsScreen`.

### Phase 3 — Implementation

Execution order: domain → data → DI → presentation layer.

**3.1 Domain: extend `MarkerSettingsRepository`**
File: `shared/src/commonMain/kotlin/ru/tcynik/meshtactics/domain/settings/repository/MarkerSettingsRepository.kt`
- Add `val geoMarkSizeLevelFlow: StateFlow<Int>`
- Add `fun getGeoMarkSizeLevel(): Int`
- Add `fun setGeoMarkSizeLevel(level: Int)`
- Add `val showGeoMarkNamesFlow: StateFlow<Boolean>`
- Add `fun getShowGeoMarkNames(): Boolean`
- Add `fun setShowGeoMarkNames(enabled: Boolean)`

**3.2 Data: implement in `AppSettings`**
File: `shared/src/commonMain/kotlin/ru/tcynik/meshtactics/data/settings/AppSettings.kt`
- Add `KEY_GEO_MARK_SIZE_LEVEL = "geo_mark_size_level"`, default 5
- Add `KEY_SHOW_GEO_MARK_NAMES = "show_geo_mark_names"`, default false
- Add `MutableStateFlow` + Flow + get/set for both, same pattern as `markerSizeLevel`

**3.3 Domain use cases (4 new files)**
Package: `app/src/main/java/ru/tcynik/meshtactics/domain/settings/usecase/`
- `GetGeoMarkSizeLevelUseCase` — `operator fun invoke(): Int = repository.getGeoMarkSizeLevel()`
- `ObserveGeoMarkSizeLevelUseCase` — `FlowUseCase<NoParams, Int>` → `repository.geoMarkSizeLevelFlow`
- `GetShowGeoMarkNamesUseCase` — `operator fun invoke(): Boolean = repository.getShowGeoMarkNames()`
- `ObserveShowGeoMarkNamesUseCase` — `FlowUseCase<NoParams, Boolean>` → `repository.showGeoMarkNamesFlow`

**3.4 DI: register use cases in `mapDataModule`**
File: `app/src/main/java/ru/tcynik/meshtactics/di/MapDataModule.kt`
- `single { GetGeoMarkSizeLevelUseCase(get()) }`
- `single { ObserveGeoMarkSizeLevelUseCase(get()) }`
- `single { GetShowGeoMarkNamesUseCase(get()) }`
- `single { ObserveShowGeoMarkNamesUseCase(get()) }`

**3.5 Presentation state: `MainUiState`**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainUiState.kt`
- Add `val geoMarkSizeLevel: Int = 5`
- Add `val showGeoMarkNames: Boolean = false`

**3.6 `MainViewModel` — inject and observe**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainViewModel.kt`
- Constructor: inject `getGeoMarkSizeLevel: GetGeoMarkSizeLevelUseCase`, `observeGeoMarkSizeLevel: ObserveGeoMarkSizeLevelUseCase`, `getShowGeoMarkNames: GetShowGeoMarkNamesUseCase`, `observeShowGeoMarkNames: ObserveShowGeoMarkNamesUseCase`
- Init block: set `geoMarkSizeLevel = getGeoMarkSizeLevel()`, `showGeoMarkNames = getShowGeoMarkNames()` in initial state
- Observe: `.onEach { _uiState.update { s -> s.copy(geoMarkSizeLevel = it) } }` and same for `showGeoMarkNames`

**3.7 DI: wire new params in `MainViewModel` constructor call**
File: `app/src/main/java/ru/tcynik/meshtactics/di/PresentationModule.kt`
- Add `getGeoMarkSizeLevel = get()`, `observeGeoMarkSizeLevel = get()`, `getShowGeoMarkNames = get()`, `observeShowGeoMarkNames = get()` to `MainViewModel` viewModel block

**3.8 `MapLibreLayer` — add params and use them**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/osd/MapLibreLayer.kt`
- Add params: `geoMarkSizeLevel: Int = 5`, `showGeoMarkNames: Boolean = false`
- Compute inside composable:
  ```kotlin
  val geoMarkIconSize = 0.3f + (geoMarkSizeLevel - 1) * 0.05f
  val trackAnchorIconSize = geoMarkIconSize * 0.7f
  ```
- Replace `iconSize = const(0.5f)` on `geo-draft-points` → `const(geoMarkIconSize)`
- Replace `iconSize = const(0.5f)` on `geo-received-points` → `const(geoMarkIconSize)`
- Replace `iconSize = const(0.35f)` on `geo-received-track-anchors` → `const(trackAnchorIconSize)`
- On `geo-received-points` SymbolLayer, when `showGeoMarkNames`, add text params (see Architecture Notes)

**3.9 `buildReceivedPointsGeoJson` — add `name` to properties**
File: `MapLibreLayer.kt` (private function at line 573)
- Change property string to include `"name":"${mark.name.jsonEscape()}"` 
- Add private extension: `private fun String.jsonEscape() = replace("\\", "\\\\").replace("\"", "\\\"")`

**3.10 Wire `MapLibreLayer` call site in `MainScreen`**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainScreen.kt`
- Pass `geoMarkSizeLevel = state.geoMarkSizeLevel` and `showGeoMarkNames = state.showGeoMarkNames` to `MapLibreLayer`

**3.11 Settings state: `SettingsUiState`**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/settings/SettingsUiState.kt`
- Add `val geoMarkSizeLevelPending: Int = 5`
- Add `val showGeoMarkNamesPending: Boolean = false`

**3.12 `SettingsViewModel` — extend with new settings**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/settings/SettingsViewModel.kt`
- Init: read from `repository.getGeoMarkSizeLevel()` and `repository.getShowGeoMarkNames()` for initial state
- Add `fun onGeoMarkSizeLevelChange(level: Int)` → `_uiState.update { it.copy(geoMarkSizeLevelPending = level) }`
- Add `fun onShowGeoMarkNamesChange(enabled: Boolean)` → `_uiState.update { it.copy(showGeoMarkNamesPending = enabled) }`
- Extend `onSave()`: also call `repository.setGeoMarkSizeLevel(pending.geoMarkSizeLevelPending)` and `repository.setShowGeoMarkNames(pending.showGeoMarkNamesPending)`; update state

**3.13 `DisplaySettingsScreen` — add slider + toggle**
File: `app/src/main/java/ru/tcynik/meshtactics/presentation/feature/settings/display/DisplaySettingsScreen.kt`
- Pass `geoMarkSizeLevelPending`, `showGeoMarkNamesPending` and callbacks to `ScreenTabContent`
- In `ScreenTabContent` after existing marker size slider, add:
  - `Text(stringResource(R.string.settings_geo_mark_size_label, sizeDp, level))` + `Slider` (same range, same steps)
  - `Row` with `Text("Имена точек на карте")` + `Switch(checked = showGeoMarkNamesPending, onCheckedChange = onShowGeoMarkNamesChange)`

**3.14 `strings.xml` — add string resources**
File: `app/src/main/res/values/strings.xml`
- `<string name="settings_geo_mark_size_label">Размер гео точек: %ddp (уровень %d)</string>`
- `<string name="settings_geo_mark_names_label">Имена точек на карте</string>`

### Phase 4 — Testing

**4.1 `SettingsViewModelTest`** — existing test file
File: `app/src/test/java/ru/tcynik/meshtactics/presentation/feature/settings/SettingsViewModelTest.kt`
- Test: `onGeoMarkSizeLevelChange` updates pending state without persisting
- Test: `onSave` persists both `geoMarkSizeLevel` and `showGeoMarkNames` to repository mock
- Test: init reads current values from repository

**4.2 `MarkerSizeConfigTest`** — check if test covers geo mark size formula, add if not
File: `app/src/test/java/ru/tcynik/meshtactics/presentation/feature/main/osd/models/MarkerSizeConfigTest.kt`
- Note: `geoMarkIconSize` formula lives inline in `MapLibreLayer`, not in `MarkerSizeConfig` — no test needed unless extracted

**4.3 Manual smoke test on device**
- Change geo mark size level → marks on map change size immediately on next compose recomposition (after save and relaunch if observable)
- Toggle names on → names appear below point marks
- Toggle names off → names disappear
- Empty name marks: no empty label rendered (MapLibre renders empty string as blank — verify visually)

### Phase 5 — Integration Review

Review changed files against Clean Architecture rules:
- Domain interface (`MarkerSettingsRepository`) has no Android/Compose imports ✓
- Use cases depend only on repository interface ✓
- `AppSettings` (data) implements domain interface; settings keys are private constants ✓
- `MapLibreLayer` is presentation-only; receives primitives, not repository references ✓

Skill: `/architect review:` — optional; changes follow existing pattern exactly.

### Phase 6 — Skill Update Review

- `/architect`: no new patterns; existing `markerSizeLevel` pattern is already canonical. No update needed.
- `/ui-designer`: no new components or design system decisions. No update needed.
- `/icon-designer`: no new icons. No update needed.
- `/planner`: no methodology gaps. No update needed.

### Phase 6b — Project Docs & Memory Update

- Update `CLAUDE.md` feature table: no new feature row needed (this extends existing Display Settings scope)
- Update `.claude/docs/geo-marks.md` — add section or note about `geoMarkSizeLevel` and `showGeoMarkNames` in the **Presentation / MapLibreLayer** subsection
- Archive this plan: move to `.claude/archive/geo-marks-display-settings.md`
- No memory files need updating (no new patterns or project state changes)

### Phase 7 — Commit Preparation

Files to stage:
- `shared/…/domain/settings/repository/MarkerSettingsRepository.kt`
- `shared/…/data/settings/AppSettings.kt`
- `app/…/domain/settings/usecase/GetGeoMarkSizeLevelUseCase.kt` (new)
- `app/…/domain/settings/usecase/ObserveGeoMarkSizeLevelUseCase.kt` (new)
- `app/…/domain/settings/usecase/GetShowGeoMarkNamesUseCase.kt` (new)
- `app/…/domain/settings/usecase/ObserveShowGeoMarkNamesUseCase.kt` (new)
- `app/…/di/MapDataModule.kt`
- `app/…/di/PresentationModule.kt`
- `app/…/presentation/feature/main/MainUiState.kt`
- `app/…/presentation/feature/main/MainViewModel.kt`
- `app/…/presentation/feature/main/osd/MapLibreLayer.kt`
- `app/…/presentation/feature/main/MainScreen.kt`
- `app/…/presentation/feature/settings/SettingsUiState.kt`
- `app/…/presentation/feature/settings/SettingsViewModel.kt`
- `app/…/presentation/feature/settings/display/DisplaySettingsScreen.kt`
- `app/…/res/values/strings.xml`
- `.claude/docs/geo-marks.md`
- `.claude/plans/geo-marks-display-settings.md` → archived to `.claude/archive/`

Proposed commit message:
```
feat(settings): добавлены настройки отображения гео точек

- слайдер "размер гео точек" (1–10) управляет iconSize геометок на карте
- тоггл "имена точек на карте" включает текстовые метки рядом с иконками
- поле name добавлено в GeoJSON-properties для слоя geo-received-points
```

## Coordination Map

```
Phase 0: skip
Phase 1: skip
Phase 2: skip
Phase 3: direct coding (steps 3.1–3.14, sequential by layer)
Phase 4: direct coding — tests (SettingsViewModelTest + manual smoke)
Phase 5: /architect review (optional — changes are pattern-identical to markerSizeLevel)
Phase 6: skill update review — no changes needed
Phase 6b: update .claude/docs/geo-marks.md, archive plan, verify CLAUDE.md
Phase 7: stage by name → propose commit → wait for confirmation → git commit
```

## Open Questions

~~1. **Empty name label**~~ → **Resolved**: use `textOptional = const(true)` on the SymbolLayer — MapLibre skips the label when `name` is empty string.
~~2. **Settings persistence timing**~~ → **Resolved**: both settings apply only after `onSave()`, same UX as existing marker size slider.
~~3. **`showGeoMarkNames` live update**~~ → **Resolved**: takes effect after save (not on toggle).

## Change Log

- 2026-05-20: created
- 2026-05-21: done | icon size range changed to 36–90dp; name labels split to separate layer `geo-received-point-labels` to fix MapLibre layer ID conflict on toggle
