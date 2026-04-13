# Plan: KMZ/KML Overlay Rendering

**Date**: 2026-04-13
**Status**: Done

## Summary

Rendering of imported KMZ/KML overlays on the MapLibre map. The user toggles display via a checkbox
in settings (Settings → Map tab). The `is_selected` state is already persisted in SQLDelight. File
import is already implemented. This feature adds:

1. **Parse-on-import** — KMZ/KML → GeoJSON (vector) + PNG (GroundOverlay), cached in `filesDir`
2. **Schema update** — paths to cached files are stored in SQLDelight
3. **Rendering in MapLibreLayer** — `GeoJsonSource` + layers for vector; `ImageSource` + `RasterLayer` for raster
4. **MainViewModel** — observes selected overlays and passes data to MapLibreLayer

---

## Architecture Notes

### Parsing: import-time (recommended by research)
- KMZ/KML are heavy to parse on every map open → parse once during import
- Results are saved to `context.filesDir/overlays/{id}/`:
  - `features.geojson` — vector (Placemarks, lines, polygons)
  - `ground_overlay.png` — raster overlay (if present)
  - `ground_overlay_bounds.json` — `{north, south, east, west}` for `LatLngQuad`
- SAF URI remains as a reference to the original; cache is the working copy for rendering

### Rendering in maplibre-compose
- **Vector**: `rememberGeoJsonSource` + `FillLayer` / `LineLayer` / `SymbolLayer` (already used in the project)
- **GroundOverlay**: `ImageSource` — need to verify if `rememberImageSource` exists in maplibre-compose 0.12.1.
  Fallback: native SDK via `MapEffect { map -> map.style?.addSource(...) }`

### Overlay rendering layer order
- Overlays are rendered **above the base raster map**, but **below node markers**
- Order: `base-raster-layer` → `overlay-ground-*` → `overlay-fill-*` → `overlay-line-*` → `overlay-symbol-*` → node layers

### SQLDelight changes
- Add columns `geo_json_path TEXT` and `ground_overlay_path TEXT` (nullable, NULL = not parsed)
- SQLDelight schema version (dev project, no migrations) → bump version in `Database.sq` or recreate DB

---

## Scope

**In scope:**
- OSMBonusPack as KML/KMZ parser (already researched)
- Extend `ImportedMapOverlay.sq` — cache paths
- `KmlOverlayParser` in `app/data/local/map/` — unzip KMZ + parse KML + serialize GeoJSON + extract PNG
- Update `ImportedMapRepositoryImpl.import()` — calls parser, saves paths
- Presentation model `OverlayRenderModel` in `presentation/feature/main/osd/models/`
- `ObserveSelectedOverlaysUseCase` — filter `isSelected == true`, reads GeoJSON from cache, loads Bitmap
- `MainUiState` — new field `selectedOverlays: ImmutableList<OverlayRenderModel>`
- `MainViewModel` — wires `ObserveSelectedOverlaysUseCase`
- `MapLibreLayer` — renders overlays (GeoJSON layers + GroundOverlay)

**Out of scope:**
- Support for `NetworkLink`, `TimeSpan`, 3D KML elements
- Full KML stylization (colors, Placemark icons) — basic styles only, refinement later
- Reparsing already imported files (not applied retroactively)

---

## Data Models

### SQLDelight (schema change)
```sql
CREATE TABLE ImportedMapOverlay (
    id                   TEXT    NOT NULL PRIMARY KEY,
    name                 TEXT    NOT NULL,
    uri                  TEXT    NOT NULL,
    created_at           INTEGER NOT NULL,
    is_selected          INTEGER NOT NULL DEFAULT 0,
    geo_json_path        TEXT,          -- NULL = не распарсено / нет вектора
    ground_overlay_path  TEXT           -- NULL = нет GroundOverlay
);
```

Добавить query:
```sql
updateParsedPaths:
UPDATE ImportedMapOverlay
SET geo_json_path = :geoJsonPath, ground_overlay_path = :groundOverlayPath
WHERE id = :id;

selectSelected:
SELECT * FROM ImportedMapOverlay WHERE is_selected = 1;
```

### Domain model `ImportedMapOverlay.kt` — extend
```kotlin
data class ImportedMapOverlay(
    val id: String,
    val name: String,
    val uri: String,
    val createdAt: Long,
    val isSelected: Boolean,
    val geoJsonPath: String?,        // path in filesDir
    val groundOverlayPath: String?,  // path in filesDir
)
```

### Presentation model (new file)
```
presentation/feature/main/osd/models/OverlayRenderModel.kt
```
```kotlin
data class OverlayRenderModel(
    val id: String,
    val geoJson: String?,          // содержимое features.geojson
    val groundOverlayBitmap: Bitmap?,
    val groundOverlayBounds: GroundOverlayBounds?,
)

data class GroundOverlayBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)
```

---

## Phase Plan

### Phase 0 — Research ✅ Done
**Result:**

1. `rememberImageSource(position: PositionQuad, bitmap: ImageBitmap)` **exists** in maplibre-compose 0.12.1
   — file `commonMain/org/maplibre/compose/sources/ImageSource.kt`
   — Option A (native `MapEffect`) is not needed
2. `PositionQuad(topLeft, topRight, bottomRight, bottomLeft)` accepts `org.maplibre.spatialk.geojson.Position`
3. `FillLayer`, `LineLayer` **exist** in `org.maplibre.compose.layers`
4. OSMBonusPack is compatible with minSdk 24 (Java 8+, confirmed by research)

### Phase 1 — Dependency + Schema
1. Add to `app/build.gradle.kts`:
   ```kotlin
   implementation("org.osmdroid:osmbonuspack:6.9.0")
   ```
2. Modify `ImportedMapOverlay.sq` — add columns + new queries
3. Extend domain model `ImportedMapOverlay.kt`
4. Update `ImportedMapRepositoryImpl.toDomain()` — map new fields
5. Bump SQLDelight schema version (or `fallbackToDestructiveMigration` in DatabaseFactory)

**Token checkpoint**: `/compact` after phase

### Phase 2 — KML Parser
**File**: `app/src/main/java/ru/tcynik/meshtactics/data/local/map/KmlOverlayParser.kt`

```kotlin
class KmlOverlayParser(private val context: Context) {
    // Returns paths to cached files (null = type absent in KML)
    suspend fun parse(id: String, uri: Uri): ParseResult

    data class ParseResult(
        val geoJsonPath: String?,
        val groundOverlayPath: String?,    // path to PNG
        val groundOverlayBoundsPath: String?, // path to JSON with bounds
    )
}
```

**Logic**:
- Determine format by extension (`uri.path?.endsWith(".kmz")`)
- KMZ → unzip to temp dir → find `.kml` inside
- KML → `KmlDocument().parseGeoJSON(context, kmlFile)` → `saveAsGeoJSON()` → write to `filesDir/overlays/{id}/features.geojson`
- Find `GroundOverlay` in `KmlDocument` → get `mIcon: Bitmap` + `mNorth/mSouth/mEast/mWest`
  → write PNG to `filesDir/overlays/{id}/ground_overlay.png`
  → write bounds JSON to `filesDir/overlays/{id}/ground_overlay_bounds.json`

### Phase 3 — Import wiring
In `ImportedMapRepositoryImpl.import()`:
- After `queries.insert(...)` call `KmlOverlayParser.parse(id, parsedUri)`
- Call `queries.updateParsedPaths(geoJsonPath, groundOverlayPath, id)`

### Phase 4 — Use case + Presentation model
1. Create `ObserveSelectedOverlaysUseCase`:
   - `ImportedMapRepository.observeSelected(): Flow<List<ImportedMapOverlay>>`
   - For each overlay reads `geoJsonPath` → file → GeoJSON string
   - Loads `groundOverlayPath` → `BitmapFactory.decodeFile()` + reads bounds JSON
   - Emits `List<OverlayRenderModel>`
2. Create `OverlayRenderModel.kt` + `GroundOverlayBounds.kt` in `presentation/feature/main/osd/models/`

### Phase 5 — MainViewModel + MainUiState
1. In `MainUiState` add:
   ```kotlin
   val selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf()
   ```
2. In `MainViewModel.init {}`:
   ```kotlin
   observeSelectedOverlays(NoParams)
       .onEach { overlays ->
           _uiState.update { it.copy(selectedOverlays = overlays.toImmutableList()) }
       }
       .launchIn(viewModelScope)
   ```
3. Pass `selectedOverlays` to `MapLibreLayer` via `MainScreen`

### Phase 6 — MapLibreLayer rendering
Add parameter:
```kotlin
selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf()
```

Inside `MaplibreMap { ... }` after the base raster map and before node markers:

**GroundOverlay** (if `overlay.groundOverlayBitmap != null`):
```kotlin
val imageSource = rememberImageSource(
    position = PositionQuad(topLeft, topRight, bottomRight, bottomLeft),
    bitmap = overlay.groundOverlayBitmap.asImageBitmap(),
)
RasterLayer(id = "overlay-ground-${overlay.id}", source = imageSource)
```

**GeoJSON vector** (if `overlay.geoJson != null`):
```kotlin
val geoSource = rememberGeoJsonSource(GeoJsonData.JsonString(overlay.geoJson))
FillLayer(id = "overlay-fill-${overlay.id}", source = geoSource, ...)
LineLayer(id = "overlay-line-${overlay.id}", source = geoSource, ...)
SymbolLayer(id = "overlay-sym-${overlay.id}", source = geoSource, ...)
```

Decision on Option A/B is made based on Phase 0 results.

### Phase 7 — DI wiring
- `app/di/MapDataModule.kt`: bind `KmlOverlayParser`, `ObserveSelectedOverlaysUseCase`
- `app/di/MainModule.kt` (or wherever `MainViewModel` lives): inject `ObserveSelectedOverlaysUseCase`

### Phase 8 — Testing
- `ObserveSelectedOverlaysUseCase` — Turbine, mock repository
- `KmlOverlayParser` — instrumental test with real KMZ/KML file from assets

### Phase 9 — Simplify + Review
- `/simplify` on `MapLibreLayer.kt`, `MainViewModel.kt`, `ImportedMapRepositoryImpl.kt`
- `/architect review` on `app/domain/map/`, `app/data/local/map/`, `MapLibreLayer.kt`

### Phase 10 — CLAUDE.md + commit
- CLAUDE.md: update KMZ/KML rendering feature status → Done
- Form commit message in Russian without `Co-Authored-By`

---

## Coordination Map

```
Phase 0: research maplibre-compose ImageSource API
Phase 1: dependency + schema + domain model
Phase 2: KmlOverlayParser
Phase 3: import wiring (extend ImportedMapRepositoryImpl)
Phase 4: ObserveSelectedOverlaysUseCase + OverlayRenderModel
Phase 5: MainUiState + MainViewModel
Phase 6: MapLibreLayer rendering (Вариант A или B по Phase 0)
Phase 7: DI
Phase 8: /tester
Phase 9: /simplify + /architect review
Phase 10: CLAUDE.md + commit
```

---

## Open Questions

1. ~~**maplibre-compose 0.12.1 ImageSource**~~ ✅ `rememberImageSource(PositionQuad, ImageBitmap)` exists
2. **OSMBonusPack GroundOverlay bounds** — clarify fields during Phase 2 implementation (likely `mNorth`/`mSouth`/`mEast`/`mWest` in `KmlGroundOverlay`)
3. **SQLDelight schema bump** — clarify in Phase 1 (find `DatabaseFactory` / schema version)

---

## Decisions

1. **Parse on import, not on rendering** — CPU-heavy operation; cache in `filesDir` ensures fast map startup
2. **Separate cache directory** `filesDir/overlays/{id}/` — isolation per overlay, easy to clean on `hide`/`delete`
3. **Bounds as JSON file** — don't clutter SQLDelight schema with extra float fields; read together with bitmap
4. **Layers rendered dynamically** from selected overlays list — no hardcoded IDs

## Change Log
- 2026-04-13: created
