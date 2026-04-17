# Plan: Map Start Position (Last Known / Default)

**Date**: 2026-04-03
**Status**: Done

## Summary

On app launch, the map centers on the last saved camera position instead of a hardcoded default.
The camera position (lat, lon, zoom) is persisted via `multiplatform-settings` (already wired).
Saving is triggered by two events: camera-idle (strategy A) and `onPause` lifecycle (strategy C).
If no position is saved yet (first launch), fall back to a hardcoded default (lat: 56.0184, lon: 92.8672)
with a TODO marker for future resolution.

`ACCESS_FINE_LOCATION` is already declared in `AndroidManifest.xml`. GPS live-tracking ("follow me") is
**out of scope** — added to wishlist.

---

## Scope

**In scope:**
- `MapCameraPosition` domain model (lat, lon, zoom)
- `LastMapPositionRepository` interface in `domain/map/`
- `LastMapPositionRepositoryImpl` in `data/local/map/` via `AppSettings` (multiplatform-settings)
- `GetLastMapPositionUseCase`, `SaveLastMapPositionUseCase`
- `MainViewModel`: read position on init, expose save method
- `MapLibreLayer`: accept initial position parameter, fire `onCameraIdle` callback
- `MainScreen`: forward camera-idle callback to ViewModel; trigger save on `onPause`
- Replace hardcoded Moscow default (37.6173, 55.7558) with Krasnoyarsk default (56.0184, 92.8672) + TODO

**Out of scope:**
- Live GPS tracking / "follow me" mode → wishlist
- GPS-based last-known position (FusedLocationProviderClient) → wishlist
- Map zoom persistence between sessions beyond what camera position provides
- Any UI changes (no new buttons, no new icons)

**Wishlist (not tracked in this plan):**
- "Follow me" mode: map tracks live GPS position
- On first launch with GPS available: center on GPS fix instead of default

---

## Key Design Decision: Save Strategy A + C

| Trigger | When | What is saved |
|---|---|---|
| **A — Camera idle** | User lifts finger; map stops moving | Current camera center + zoom |
| **C — onPause** | Activity goes to background | Current camera center + zoom |

Both triggers call the same `SaveLastMapPositionUseCase`. No deduplication needed — saving the same
value twice is harmless.

---

## Existing Infrastructure (no setup needed)

| Asset | Status | Location |
|---|---|---|
| `GeoPoint(lat, lon)` | ✅ exists | `domain/marker/model/GeoPoint.kt` |
| `multiplatform-settings` | ✅ wired | `shared/build.gradle.kts` + `AndroidModule.kt` |
| `AppSettings` wrapper | ✅ exists | `shared/commonMain/.../AppSettings.kt` |
| `ACCESS_FINE_LOCATION` | ✅ declared | `AndroidManifest.xml` |
| MapLibre Compose | ✅ active | `maplibre-compose:0.12.1` |

**New dependency needed:** None. `play-services-location` (FusedLocationProviderClient) is NOT required
for this feature — GPS tracking is out of scope.

---

## Architecture Notes

### New domain model

```kotlin
// domain/map/model/MapCameraPosition.kt
data class MapCameraPosition(
    val lat: Double,
    val lon: Double,
    val zoom: Double,   // Double — matches CameraPosition.zoom in maplibre-compose
)
```

### New domain repository interface

```kotlin
// domain/map/repository/LastMapPositionRepository.kt
interface LastMapPositionRepository {
    fun get(): MapCameraPosition?
    fun save(position: MapCameraPosition)
}
```

### New use cases

```kotlin
// domain/map/usecase/GetLastMapPositionUseCase.kt
class GetLastMapPositionUseCase(private val repository: LastMapPositionRepository) {
    operator fun invoke(): MapCameraPosition? = repository.get()
}

// domain/map/usecase/SaveLastMapPositionUseCase.kt
class SaveLastMapPositionUseCase(private val repository: LastMapPositionRepository) {
    operator fun invoke(position: MapCameraPosition) = repository.save(position)
}
```

### Data implementation

`LastMapPositionRepositoryImpl` injects `Settings` directly (NOT `AppSettings`) — keeps `AppSettings`
focused on its existing keys and avoids coupling unrelated concerns.

```kotlin
// data/local/map/LastMapPositionRepositoryImpl.kt
private const val KEY_LAT  = "last_map_lat"
private const val KEY_LON  = "last_map_lon"
private const val KEY_ZOOM = "last_map_zoom"

// TODO: replace with GPS first-fix or user-configurable home position
private const val DEFAULT_LAT  = 56.0184
private const val DEFAULT_LON  = 92.8672
private const val DEFAULT_ZOOM = 10.0   // Double

class LastMapPositionRepositoryImpl(private val settings: Settings) : LastMapPositionRepository {
    override fun get(): MapCameraPosition? {
        val lat  = settings.getDoubleOrNull(KEY_LAT)  ?: return null
        val lon  = settings.getDoubleOrNull(KEY_LON)  ?: return null
        val zoom = settings.getDoubleOrNull(KEY_ZOOM) ?: return null
        return MapCameraPosition(lat, lon, zoom)
    }
    override fun save(position: MapCameraPosition) {
        settings.putDouble(KEY_LAT,  position.lat)
        settings.putDouble(KEY_LON,  position.lon)
        settings.putDouble(KEY_ZOOM, position.zoom)
    }
}
```

### MainUiState change

```kotlin
data class MainUiState(
    val tileUrlTemplate: String = "",
    val initialCameraPosition: MapCameraPosition = MapCameraPosition(56.0184, 92.8672, 10.0),
)
```

### MainViewModel changes

```kotlin
private val DEFAULT_POSITION = MapCameraPosition(56.0184, 92.8672, 10.0)
// TODO: replace default with GPS first-fix or user-configurable home position

class MainViewModel(
    getTileUrl: GetTileUrlUseCase,
    getLastPosition: GetLastMapPositionUseCase,
    private val saveLastPosition: SaveLastMapPositionUseCase,
) : ViewModel() {

    init {
        val position = getLastPosition() ?: DEFAULT_POSITION
        _uiState.update { it.copy(initialCameraPosition = position) }
    }

    fun onCameraPositionChanged(position: MapCameraPosition) {
        saveLastPosition(position)
    }
}
```

### MapLibreLayer signature change

```kotlin
@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,          // NEW
    onCameraPositionChanged: (MapCameraPosition) -> Unit, // NEW — camera-idle callback
)
```

### Camera-idle in MapLibre Compose (resolved in Phase 0)

```kotlin
// CameraPosition uses Position(longitude, latitude) — longitude FIRST
val cameraState = rememberCameraState(
    firstPosition = CameraPosition(
        target = Position(
            longitude = initialCameraPosition.lon,
            latitude  = initialCameraPosition.lat,
        ),
        zoom = initialCameraPosition.zoom,
    )
)

// Camera idle detection — isCameraMoving becomes false when camera stops
LaunchedEffect(cameraState.isCameraMoving) {
    if (!cameraState.isCameraMoving) {
        val pos = cameraState.position
        onCameraPositionChanged(
            MapCameraPosition(
                lat  = pos.target.latitude,
                lon  = pos.target.longitude,
                zoom = pos.zoom,
            )
        )
    }
}
```

**Note**: `isCameraMoving` is also `false` on initial composition before the user touches the map.
Guard against spurious save on startup: only call `onCameraPositionChanged` if position differs from
`initialCameraPosition`, OR ignore the first emission via `drop(1)` on a derived flow.

### onPause save (strategy C)

`cameraState` lives in `MapLibreLayer`; `MainScreen` needs access to current position for onPause.
Two options:

- **Option C1**: hoist `cameraState` up to `MainScreen`, pass it down to `MapLibreLayer`
- **Option C2**: `MainScreen` tracks last reported position via `remember` (updated by `onCameraPositionChanged` callback)

Option C2 is cleaner — no extra state hoisting, `MapLibreLayer` stays self-contained.

```kotlin
// MainScreen.kt
var lastSavedPosition by remember { mutableStateOf(uiState.initialCameraPosition) }

// MapLibreLayer callback updates local tracker
MapLibreLayer(
    onCameraPositionChanged = { pos ->
        lastSavedPosition = pos
        viewModel.onCameraPositionChanged(pos)  // strategy A save
    }
)

// Strategy C: save on pause via last known position
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE) {
            viewModel.onCameraPositionChanged(lastSavedPosition)
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

---

## Phase Plan

### Phase 0 — Research: MapLibre Compose Camera Idle API ✅ DONE (2026-04-03)

**Findings:**

1. **Camera idle** — `CameraState.isCameraMoving: Boolean` exists (`org.maplibre.compose.camera.CameraState`).
   `MapEffect` does NOT exist in maplibre-compose 0.12.1. No `onCameraIdle` callback on `MaplibreMap`.
   **Solution**: `LaunchedEffect(cameraState.isCameraMoving) { if (!isCameraMoving) save() }` — clean Compose approach.

2. **AppSettings** — thin wrapper over `com.russhwolf.settings.Settings`.
   `Settings.putDouble` / `getDoubleOrNull` are available. `AppSettings` should NOT be extended —
   `LastMapPositionRepositoryImpl` injects `Settings` directly (same pattern, cleaner separation).

3. **CameraPosition** — `target: Position` from `org.maplibre.spatialk.geojson.Position`.
   Constructor: `Position(longitude, latitude)` — **longitude first!**
   `zoom: Double` (not Float — `MapCameraPosition` must store zoom as `Double`).

---

### Phase 1 — Architecture Design

**Goal**: Approved layer decomposition and file list before any code is written.

**Tasks:**
1. Design `MapCameraPosition` model, `LastMapPositionRepository` interface, two use cases
2. Decide which layer owns the "camera-idle → save" wiring (ViewModel or Screen)
3. Confirm `AppSettings` is injectable into `LastMapPositionRepositoryImpl` via Koin
4. Produce file list: what is created, what is modified

**Skill**: `/architect feature: persist and restore map camera position on app start — see plan map-start-position.md`
**Output**: Approved architecture plan; confirmed file list

---

### Phase 2 — UI / Icon Design

**Skipped** — no new UI elements, no new icons.

---

### Phase 3 — Implementation

**Goal**: Working feature across all layers; app compiles and behaves correctly.

**Tasks (in order):**
1. Create `domain/map/model/MapCameraPosition.kt`
2. Create `domain/map/repository/LastMapPositionRepository.kt`
3. Create `domain/map/usecase/GetLastMapPositionUseCase.kt`
4. Create `domain/map/usecase/SaveLastMapPositionUseCase.kt`
5. Create `data/local/map/LastMapPositionRepositoryImpl.kt` using `AppSettings` — keys: `last_map_lat`, `last_map_lon`, `last_map_zoom`; default (56.0184, 92.8672, 10f) with `// TODO` comment
6. Add `LastMapPositionRepository` binding to `MapDataModule.kt`
7. Update `MainUiState.kt` — add `initialCameraPosition: MapCameraPosition`
8. Update `MainViewModel.kt` — inject `GetLastMapPositionUseCase` + `SaveLastMapPositionUseCase`; read position in `init`; expose `onCameraPositionChanged()`
9. Update `PresentationModule.kt` — add new use cases and repository to DI graph
10. Update `MapLibreLayer.kt`:
    - Accept `initialCameraPosition: MapCameraPosition` parameter
    - Accept `onCameraPositionChanged: (MapCameraPosition) -> Unit` parameter
    - Replace hardcoded default position with `initialCameraPosition`
    - Wire camera-idle callback (strategy A — per Phase 0 findings)
11. Update `MainScreen.kt`:
    - Pass `initialCameraPosition` and `onCameraPositionChanged` to `MapLibreLayer`
    - Add `DisposableEffect` + `LifecycleEventObserver` for `ON_PAUSE` → save (strategy C)

**Skill**: Direct coding (EnterPlanMode before starting)
**Output**: Buildable, working feature committed

---

### Phase 4 — Testing

**Goal**: Feature verified at unit level and manually on device.

**Tasks:**
1. Unit test `GetLastMapPositionUseCase` — mock `LastMapPositionRepository`; verify returns `null` when repo returns `null`, returns position when repo has one
2. Unit test `SaveLastMapPositionUseCase` — mock repo; verify `save()` called with correct `MapCameraPosition`
3. Manual smoke test:
   - First launch: confirm map opens at (56.0184, 92.8672)
   - Pan map → kill app → relaunch: confirm map opens at panned position
   - Background app → relaunch: confirm position preserved

**Skill**: Direct coding (unit tests) + manual device test
**Output**: 2 passing unit tests; manual verification passed

---

### Phase 5 — Integration Review

**Goal**: Confirm no Clean Architecture violations in new and modified files.

**Tasks:**
1. Review `data/local/map/LastMapPositionRepositoryImpl.kt` — no domain knowledge leak
2. Review `MainViewModel.kt` — uses only use cases, not repository directly
3. Review `MapLibreLayer.kt` — no direct use case or repository calls
4. Review `MapDataModule.kt` — correct `single<>` binding

**Skill**: `/architect review: data/local/map, domain/map, presentation/feature/main — map-start-position feature`
**Output**: Review report; violations fixed before phase closes

---

### Phase 6 — Skill Update Review

**Tasks per skill:**
- `/architect`: Check if `AppSettings` persistence pattern (key constants, `get`/`put` via `LastMapPositionRepositoryImpl`) should be documented as canonical for future local-only repositories.
- `/ui-designer`: No changes — no new UI elements introduced.
- `/icon-designer`: No changes — no new icons.
- `/planner`: No methodology gaps. No new skills created.

**Skill**: Direct edit of `.claude/commands/architect.md`
**Output**: Updated skill files or explicit "no changes needed" per skill

---

## Coordination Map

```
Phase 0: [Explore agent — MapLibre camera idle API + AppSettings key API]
Phase 1: /architect feature: persist and restore map camera position — see map-start-position.md
Phase 2: [skipped — no UI changes]
Phase 3: [direct coding — EnterPlanMode]
Phase 4: [direct coding — unit tests + manual device test]
Phase 5: /architect review: data/local/map, domain/map, presentation/feature/main
Phase 6: [direct edit — .claude/commands/architect.md]
```

---

## Open Questions

1. ~~**Camera idle API**~~ ✅ `CameraState.isCameraMoving: Boolean` — use `LaunchedEffect`. No `MapEffect`, no native listener needed.
2. ~~**AppSettings key API**~~ ✅ `Settings.putDouble`/`getDoubleOrNull` available. `LastMapPositionRepositoryImpl` injects `Settings` directly, not `AppSettings`.
3. ~~**Camera position in onPause**~~ ✅ Strategy C2: `MainScreen` tracks last position via `remember`, updated by `onCameraPositionChanged` callback from `MapLibreLayer`.
4. **Spurious save on startup**: `isCameraMoving` is `false` on first composition — may trigger save before user touches map. Resolve in Phase 3: guard with `var hasUserMoved by remember { mutableStateOf(false) }`, set to `true` on first `isCameraMoving == true`.

---

## Change Log

- 2026-04-03: created — save strategy A+C confirmed; GPS/follow-me out of scope; default (56.0184, 92.8672)
- 2026-04-03: Phase 0 done — camera idle via isCameraMoving; Settings injected directly; Position(lon, lat); spurious-save guard identified
- 2026-04-03: Phases 1–6 done — feature complete, manual smoke test passed, no architecture violations
- 2026-04-03: Phase 6 — architect.md updated: sync use case pattern, Settings injection convention, 2 new anti-patterns; ui-designer/icon-designer/planner — no changes needed
