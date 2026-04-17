# Plan: KMZ/KML File Import

**Date**: 2026-04-13
**Status**: Done

## Summary

Allows the user to import KMZ and KML overlay files from the device file system via Android SAF
(Storage Access Framework) directly from the Map tab of the Settings screen. Imported files are
listed with name and creation date. Each item has a dropdown menu with two actions: "Hide" (removes
the record from the app's database, original file untouched) and "Delete" (shows a confirmation
dialog, then deletes the file from the system and removes the record). The "Add" button launches the
OS file picker filtered to KMZ/KML MIME types.

## Scope

**In scope:**
- Android SAF file picker for `.kmz` / `.kml` (MIME: `application/vnd.google-earth.kmz`,
  `application/vnd.google-earth.kml+xml`)
- Persistable URI permission on import (READ, and optionally WRITE for deletion)
- SQLDelight table `ImportedMapOverlay` — stores: id, name, uri, created_at, is_selected
- `is_selected` persisted to DB and restored between sessions
- Domain model `ImportedMapOverlay`, repository interface `ImportedMapRepository`, use cases:
  `ObserveImportedMapsUseCase`, `ImportMapFileUseCase`, `HideImportedMapUseCase`,
  `DeleteImportedMapUseCase`, `ToggleImportedMapSelectionUseCase`
- `SettingsViewModel` wired to real data (Flow from repository)
- `SettingsUiState.mapItems` replaced with live data
- `MapItem.kt` moved to `presentation/feature/settings/models/` (CLAUDE.md compliance)
- Confirmation dialog for the "Delete" action (standard M3 `AlertDialog`)
- String resources for all new UI text

**Out of scope:**
- Rendering KMZ/KML overlays on the map (future feature)
- Copying file to internal storage — deferred TODO (currently only URI reference is stored)
- "Show again" (re-import hidden items)
- Progress indication during import

## Architecture Notes

### Layer assignment
- Domain: `app/domain/map/` (Android-only feature, consistent with existing map domain)
- Data: `app/data/local/map/` + new `ImportedMapOverlay.sq` in `shared/sqldelight/`
- Presentation: `app/presentation/feature/settings/` (existing, extending `SettingsViewModel`)

### File storage strategy
**Persistable SAF URI** — store URI string in SQLDelight, call
`takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` on import.
- Hide = `releasePersistableUriPermission` + delete DB row
- Delete = `ContentResolver.delete(uri)` + release permission + delete DB row

> **TODO (deferred)**: copy file to `context.filesDir` for full offline reliability and
> independence from external storage availability. Implement after core map rendering is done.

### SQLDelight table (draft)
```sql
CREATE TABLE ImportedMapOverlay (
    id          TEXT    NOT NULL PRIMARY KEY,
    name        TEXT    NOT NULL,
    uri         TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    is_selected INTEGER NOT NULL DEFAULT 0
);
```

### Key interfaces (draft)
```kotlin
// app/domain/map/repository/ImportedMapRepository.kt
interface ImportedMapRepository {
    fun observeAll(): Flow<List<ImportedMapOverlay>>
    suspend fun import(uri: String, name: String, createdAt: Long)
    suspend fun hide(id: String)         // deletes DB row; original file untouched
    suspend fun delete(id: String)       // deletes file via ContentResolver + DB row
    suspend fun setSelected(id: String, selected: Boolean)
}
```

## Phase Plan

### Phase 0 — Research
- **Goal**: Confirm SAF URI persistence and file deletion behaviour across API 26–36
- **Tasks**: MIME types for KMZ/KML, `takePersistableUriPermission` lifetime, `ContentResolver.delete` scope
- **Skill**: `/research android-saf-kmz-kml`
- **Output**: `.claude/research/android-saf-kmz-kml.md`
- **Token checkpoint**: run `/compact` after Phase 0

### Phase 1 — Architecture Design
- **Goal**: Approved architecture plan — layers, interfaces, SQLDelight schema, DI plan
- **Skill**: `/architect feature: ImportedMapOverlay — SAF file picker for KMZ/KML, SQLDelight storage, hide/delete lifecycle`
- **Output**: architecture plan (domain model, repository, use case signatures, DI wiring)
- **Token checkpoint**: run `/compact` after Phase 1

### Phase 2 — UI Design
- **Goal**: Decision on delete confirmation dialog pattern
- **Skill**: `/ui-designer component: delete confirmation dialog`
- **Output**: use standard M3 `AlertDialog` or project-specific component

### Phase 3 — Implementation
Order: SQLDelight → domain → data → DI → presentation

1. Add `ImportedMapOverlay.sq` to `shared/src/commonMain/sqldelight/` (includes `is_selected` column)
2. Create `app/domain/map/model/ImportedMapOverlay.kt`
3. Create `app/domain/map/repository/ImportedMapRepository.kt`
4. Create use cases: `ObserveImportedMapsUseCase`, `ImportMapFileUseCase`, `HideImportedMapUseCase`, `DeleteImportedMapUseCase`, `ToggleImportedMapSelectionUseCase`
5. Create `app/data/local/map/ImportedMapRepositoryImpl.kt` (SAF + SQLDelight)
6. Extend `app/di/MapDataModule.kt` with new bindings
7. Move `MapItem.kt` → `presentation/feature/settings/models/MapItem.kt`
8. Add file picker launcher to `MapTabContent` (`rememberLauncherForActivityResult`)
9. Wire hide/delete/toggle events + confirmation dialog in `SettingsScreen.kt`
10. Extend `SettingsViewModel` with `onAddMap`, `onHideMap(id)`, `onDeleteMap(id)`, `onConfirmDelete`, `onDismissDeleteDialog`, `onToggleSelection(id)`
11. Replace dummy `mapItems` in `SettingsUiState` with collected Flow
12. Add string resources to `strings.xml`

After: run `/simplify` on changed files.

### Phase 4 — Testing
- `ObserveImportedMapsUseCase` via Turbine
- `SettingsViewModel` hide/delete/toggleSelection via MockK
- `ImportedMapRepositoryImpl` integration with in-memory SQLDelight (verify `is_selected` persistence)

### Phase 5 — Integration Review
- `/architect review: app/domain/map/, app/data/local/map/, app/di/MapDataModule.kt`

### Phase 6 — Skill Update Review
- `/architect` — SAF URI as TEXT in SQLDelight; Android-only domain in `app/domain/`
- `/ui-designer` — confirmation dialog pattern
- `/icon-designer` — no changes
- `/planner` — verify skills snapshot is current

### Phase 6b — Docs & Memory Update
- CLAUDE.md: add `KMZ/KML Import` to feature table
- Set plan status → Done
- Update memory files

### Phase 7 — Commit Preparation
- `git status` → stage by name → propose RU commit message → wait for user confirmation → commit

## Coordination Map

```
Phase 0: /research android-saf-kmz-kml → .claude/research/ → [/compact]
Phase 1: /architect feature: ImportedMapOverlay → [/compact]
Phase 2: /ui-designer component: delete confirmation dialog
Phase 3: [direct coding: sq → domain → data → DI → presentation] → /simplify
Phase 4: /tester + [direct coding — tests]
Phase 5: /architect review: app/domain/map/, app/data/local/map/, MapDataModule.kt
Phase 6: [skill update review]
Phase 6b: [CLAUDE.md + plan file + memory/]
Phase 7: [stage by name] → [RU commit message] → [wait confirmation] → git commit
```

## Decisions

1. **File storage** — store SAF URI only (no copy to `filesDir`). Copy deferred as TODO for later.
2. **Checkbox (`is_selected`)** — implemented and persisted to SQLDelight `is_selected` column. State survives app restarts.
3. **"Hide" semantics** — physically deletes the DB row (no "unhide" path).
4. **`ContentResolver.delete` scope** — confirm in Phase 0 research whether it works without `MANAGE_EXTERNAL_STORAGE` on API 26–36.

## Change Log
- 2026-04-13: created
- 2026-04-13: decisions made — URI-only storage (copy deferred), checkbox persisted to DB, hide = delete row
