# Plan: GPS User Position Marker on Map

**Date**: 2026-04-03
**Status**: Done

## Summary

Display the device's current GPS position as a marker on the MapLibre map. The marker updates continuously as the user moves. Originally planned with `LocationPuck` from `maplibre-compose:0.12.1`, but replaced at runtime with a manual `CircleLayer + GeoJsonData.JsonString` implementation due to a bug in `spatialk:geojson:0.6.0` (see Decision #5). Location permissions are already declared in the manifests and requested at runtime by `BlePermissionGuard`.

## Scope

**In scope:**
- `MeshLocationProvider` — adapter implementing `org.maplibre.compose.location.LocationProvider`, wrapping `LocationRepository.getLocations()` (Phase 3a)
- `LocationPuck` in `MapLibreLayer`, driven by `rememberUserLocationState(meshLocationProvider)`
- DI wiring for `MeshLocationProvider` in `app/di/LocationDomainModule.kt`
- `MainViewModel` / `MainUiState` changes minimal or none (location state lives in `UserLocationState`, not in `MainUiState`)
- Phase 3b: replace `MeshLocationProvider` internals with a direct OS-level `LocationProvider` at 5 s interval (separate `AppLocationProvider`)

**Out of scope:**
- Custom animated / pulsing icon (deferred, TODO left in code)
- "Center on me" button / camera auto-follow (see `LocationTrackingEffect`)
- Background location tracking
- Permission request UI changes (already handled by `BlePermissionGuard`)

## Architecture Notes

### Existing infrastructure (no new code needed)
- `LocationRepository` interface — `mesh/repository/LocationRepository.kt`
  - `getLocations(): Flow<Location>` (typealiased to `android.location.Location`)
  - `receivingLocationUpdates: StateFlow<Boolean>`
- `LocationRepositoryImpl` — 30 s update interval, high accuracy, `callbackFlow`
- Permissions already in both manifests; `BlePermissionGuard` already requests `ACCESS_FINE_LOCATION`
- `org.maplibre.compose.location.LocationPuck` — composable: `CircleLayer` (dot + shadow + accuracy) + `SymbolLayer` (bearing). Takes `UserLocationState`.
- `org.maplibre.compose.location.LocationProvider` — interface with `val location: Flow<Location?>`
- `org.maplibre.compose.location.rememberUserLocationState(locationProvider)` — factory composable

### maplibre-compose `Location` type
`org.maplibre.compose.location.Location` is the library's own type (not `android.location.Location`).
Fields needed for the adapter: `position: Position` (lng/lat), `accuracy: Float`, `bearing: Double?`, `bearingAccuracy: Double?`, `timestamp: ComparableTimeMark`.

### New code — Phase 3a

| Layer | Artifact | Location |
|---|---|---|
| DI / Adapter | `MeshLocationProvider : LocationProvider` | `app/di/location/MeshLocationProvider.kt` |
| DI | `LocationDomainModule` (Koin) | `app/di/LocationDomainModule.kt` |
| Presentation | `LocationPuck` in `MapLibreLayer` | `app/presentation/feature/main/osd/MapLibreLayer.kt` |
| Presentation | `userLocationProvider` param on `MapLibreLayer` | same file |
| Presentation | pass provider from DI into `MapLibreLayer` | `app/presentation/feature/main/MainScreen.kt` |

`MainUiState` and `MainViewModel` are **not changed** — location state is owned by `UserLocationState` inside the composable, no ViewModel involvement needed.

### New code — Phase 3b

| Layer | Artifact | Location |
|---|---|---|
| DI / Adapter | `AppLocationProvider : LocationProvider` | `app/di/location/AppLocationProvider.kt` |
| DI | replace `MeshLocationProvider` binding with `AppLocationProvider` | `app/di/LocationDomainModule.kt` |

`AppLocationProvider` registers its own `LocationManagerCompat` request at 5 s, independently of `LocationRepository`. `MeshLocationProvider` is deleted. `LocationRepository` / `AndroidMeshLocationManager` stay unchanged.

### Data flow (Phase 3a)
```
LocationRepositoryImpl (mesh)
  → getLocations(): Flow<android.location.Location>
  → MeshLocationProvider maps android.location.Location → maplibre Location
  → rememberUserLocationState(meshLocationProvider)
  → UserLocationState.location: Location?
  → LocationPuck(locationState = userLocationState, ...)
```

### Data flow (Phase 3b)
```
AppLocationProvider (app/di/location)
  → LocationManagerCompat 5 s request
  → val location: Flow<maplibre Location?>
  → rememberUserLocationState(appLocationProvider)
  → UserLocationState.location: Location?
  → LocationPuck(locationState = userLocationState, ...)
```

### Location arrow usage (final implementation — Compose overlay)

`LocationPuck`, `rememberUserLocationState`, and `CircleLayer` are **not used** for the user position — see Decision #5 and #7.

The arrow is a `Image` composable rendered as a Compose overlay on top of `MaplibreMap` inside `MainScreen`. Position is derived from `cameraState.projection.screenLocationFromPosition()`, which converts GPS coordinates to screen DP offsets. Rotation accounts for both device azimuth and camera bearing.

```kotlin
// Inside MainScreen Box { } — after MapLibreLayer
val projection = cameraState.projection
if (projection != null && mapSize != IntSize.Zero && currentLocation != null) {
    val screenOffset: DpOffset = projection.screenLocationFromPosition(
        Position(
            longitude = currentLocation!!.position.longitude,
            latitude  = currentLocation!!.position.latitude,
        ),
    )
    val halfIconPx = with(density) { 18.dp.roundToPx() }  // icon = 36dp
    Image(
        painter = painterResource(R.drawable.ic_navigation_arrow),
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(arrowOffsetX - halfIconPx, arrowOffsetY - halfIconPx) }
            .size(36.dp)
            .rotate(bearing + 180f - cameraBearing),
    )
}
```

**Why overlay, not map layer**: a Compose `Image` overlay allows GPU-free rotation via `Modifier.rotate` without re-encoding a GeoJSON bearing property and without a MapLibre `SymbolLayer` / sprite sheet setup. The map layer approach would require registering a sprite image and a `SymbolLayer` with `icon-rotate` expression — more complexity for no benefit at this stage.

## Phase Plan

### Phase 0 — Research ✅ (completed)
- **Findings**: `LocationRepository` fully implemented in `mesh`; permissions declared; `maplibre-compose:0.12.1` has `LocationPuck` + `LocationProvider` — no `PointAnnotation` exists in this library.
- **Output**: this plan

### Phase 1 — Architecture Design ✅ (completed via open questions resolution)
- Decided: use `LocationPuck` + `LocationProvider` adapter (Variant A)
- Decided: separate `app/di/LocationDomainModule.kt`
- Decided: `MainUiState` not changed — `UserLocationState` lives in composable scope
- Decided: phased interval approach (30 s first, then 5 s separately)

### Phase 2 — UI Design ✅ (completed)
- `LocationPuck` API confirmed from sources
- No custom assets needed for Phase 3a; TODO left for custom icon
- `LocationPuckColors` / `LocationPuckSizes` use defaults initially

### Phase 3a — Implementation (30 s, via LocationRepository) ✅
- **Goal**: working GPS marker on map, full stack validated
- **Order**:
  1. Create `app/di/location/MeshLocationProvider.kt` — implement `LocationProvider`, map `android.location.Location` → maplibre `Location`
  2. Create `app/di/LocationDomainModule.kt` — bind `MeshLocationProvider` as `LocationProvider` singleton
  3. `MapLibreLayer`: add `locationProvider: LocationProvider` param, add `LocationPuck` block with TODO comment
  4. `MainScreen`: inject `LocationProvider` from DI and pass to `MapLibreLayer`
- **Skill**: direct coding (EnterPlanMode before starting)
- **Output**: committed, buildable code

### Phase 4a — Testing (Phase 3a) ✅
- **Goal**: Phase 3a stack verified
- **Tasks**:
  - Unit test `MeshLocationProvider`: mock `LocationRepository`, assert `Flow<maplibre Location?>` maps fields correctly
  - Manual smoke test: launch app, verify `LocationPuck` dot appears at device GPS position
- **Output**: passing tests + smoke test confirmation

### Phase 3b — Implementation (5 s, direct OS request) ✅
- **Goal**: 5 s update interval without affecting Mesh position broadcast
- **Order**:
  1. Create `app/di/location/AppLocationProvider.kt` — `LocationProvider` with own `LocationManagerCompat` registration at 5 s
  2. In `LocationDomainModule`: replace `MeshLocationProvider` binding with `AppLocationProvider`
  3. Delete `MeshLocationProvider.kt`
- **Skill**: direct coding
- **Output**: committed, buildable code

### Phase 4b — Testing (Phase 3b) ✅
- **Goal**: 5 s interval verified, Mesh broadcast unaffected
- **Tasks**:
  - Unit test `AppLocationProvider`: verify OS request uses 5 s interval
  - Manual smoke test: confirm marker updates more frequently; confirm Meshtastic position broadcast cadence unchanged
- **Output**: passing tests + smoke test confirmation

### Phase 5 — Integration Review ✅
- **Goal**: no Clean Architecture violations
- **Tasks**: confirm `app`-layer only touches `LocationRepository` interface (not `Impl`); confirm no `mesh`-internal types leak into `app/presentation`
- **Skill**: `/architect review: app/di/location/, app/presentation/feature/main/`
- **Output**: review report, violations fixed

### Phase 6 — Skill Update Review ✅
- **Goal**: skills stay in sync with patterns established here
- **Tasks**:
  - `/architect` — add pattern: "maplibre-compose LocationProvider adapter bridges mesh LocationRepository to map UI; interval concerns handled in app/di/location/, not in mesh module"
  - `/ui-designer` — no new design tokens → **no changes needed**
  - `/icon-designer` — no new icons → **no changes needed**
  - `/planner` — no methodology gap → **no changes needed**
- **Output**: updated architect skill (or explicit no-op for each)

## Coordination Map

```
Phase 0:  [Explore agent — completed]
Phase 1:  [open questions resolution — completed]
Phase 2:  [direct code check — completed]
Phase 3a: [direct coding — EnterPlanMode]
Phase 4a: [direct coding — unit tests + smoke test]
Phase 3b: [direct coding — EnterPlanMode]
Phase 4b: [direct coding — unit tests + smoke test]
Phase 5:  /architect review: app/di/location/, app/presentation/feature/main/
Phase 6:  [skill update review — architect.md only if needed]
```

## Decisions

1. **Marker component**: `LocationPuck` from `maplibre-compose:0.12.1` — no `PointAnnotation` exists in this library.
2. **ViewModel involvement**: none — `UserLocationState` lives in composable scope via `rememberUserLocationState`.
3. **DI module**: separate `app/di/LocationDomainModule.kt` — anticipated growth of location-related use cases.
4. **Update interval — phased**:
   - Phase 3a: 30 s via `LocationRepository` — validates full stack with zero new OS registrations
   - Phase 3b: 5 s via `AppLocationProvider` with own OS request — `LocationRepository` / Mesh broadcast unchanged
5. **`LocationPuck` replaced by Compose overlay `Image`**: `LocationPuck` / `rememberUserLocationState` not used. Root cause: `spatialk:geojson:0.6.0` (transitive dep of `maplibre-compose:0.12.1`) crashes when serializing an empty `FeatureCollection()` — `firstNotNullOf { it.properties }` throws `NoSuchElementException` on an empty features list. Initial fix used `CircleLayer + GeoJsonData.JsonString`; then superseded by Decision #7.

6. **`receivingLocationUpdates` flag**: Irrelevant in final implementation — `currentLocation == null` check in the overlay naturally hides the arrow.

7. **`CircleLayer` replaced by Compose overlay `Image`**: bearing-aware directional arrow (`ic_navigation_arrow`, 36dp) rendered as Compose `Image` overlay on top of `MaplibreMap`. Position from `cameraState.projection.screenLocationFromPosition()`. Rotation: `bearing + 180f - cameraBearing` (device azimuth corrected for camera tilt; `+180f` compensates for icon's default south-facing orientation). `MapLibreLayer` no longer takes `locationProvider` or `orientationProvider` parameters — location state is owned entirely by `MainScreen`.

## Open Questions

_None — all resolved._

## Change Log

- 2026-04-03: created
- 2026-04-03: open questions resolved — separate LocationDomainModule, 5 s update interval, hide marker when location unavailable
- 2026-04-04: major revision — replaced PointAnnotation (non-existent API) with LocationPuck; replaced ObserveUserLocationUseCase+MainUiState approach with LocationProvider adapter pattern; split Phase 3 into 3a (30 s) + 3b (5 s); Phases 1 and 2 marked complete
- 2026-04-05: implementation complete — AppLocationProvider (5 s OS), LocationDomainModule, MapLibreLayer+LocationPuck, NavGraph injection; architect review: koinInject() moved from MainScreen to NavGraph; architect.md updated with LocationProvider adapter pattern; all phases Done
- 2026-04-05: runtime fix — `LocalStyleNode` crash fixed (rememberUserLocationState moved inside MaplibreMap lambda); then `LocationPuck` replaced with `CircleLayer + GeoJsonData.JsonString` to bypass `spatialk:geojson:0.6.0` FeatureCollection serialization bug; architect.md and plan updated
- 2026-04-08: review — replaced `CircleLayer` with Compose overlay `Image` (bearing-aware arrow); fixed arrow centering offset (12dp → 18dp for 36dp icon); removed dead code from `MapLibreLayer` (`locationProvider`/`orientationProvider` params, dead GeoJSON block, unused `drawableToImageBitmap`); plan updated (Decision #5 split into #5/#7, Architecture Notes updated)
