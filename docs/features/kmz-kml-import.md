# KMZ/KML File Import

## What it does
Lets the user import KMZ/KML overlay files from device storage via Android SAF. Imported files are listed in Settings → Map tab with Hide and Delete actions. Selection state persists between sessions.

## Key classes
- `ImportedMapOverlay` — domain model; `domain/map/model/`
- `ImportedMapRepository` / `ImportedMapRepositoryImpl` — SAF + SQLDelight; `domain/map/repository/`, `data/local/map/`
- Use cases: `ObserveImportedMapsUseCase`, `ImportMapFileUseCase`, `HideImportedMapUseCase`, `DeleteImportedMapUseCase`, `ToggleImportedMapSelectionUseCase`; `domain/map/usecase/`
- `SettingsViewModel` — wired to real data via Flow from repository
- SQLDelight table `ImportedMapOverlay` — id, name, uri, created_at, is_selected, geo_json_path, ground_overlay_path

## Non-obvious decisions
- **SAF URI only, no file copy**: file stays at original location, URI reference stored in SQLDelight. `takePersistableUriPermission(FLAG_GRANT_READ_URI_PERMISSION)` called on import to survive process restarts. File copy to `filesDir` is a TODO for offline reliability.
- **Hide = delete DB row**: "Hide" physically removes the record (and releases the persistable URI permission) — no "show again" path.
- **Delete = `ContentResolver.delete(uri)` + release permission + delete row**: works without `MANAGE_EXTERNAL_STORAGE` on API 26–36 for SAF-issued URIs.
- `is_selected` persists to SQLDelight and is restored between sessions.

## Known limitations / planned extensions
- File is stored by URI — if the user moves/deletes the file outside the app, the overlay silently breaks. File copy to `filesDir` is deferred.
- No "show again" (re-import hidden items)

## Source
Plan: `docs/archive/kmz-kml-import.md`
