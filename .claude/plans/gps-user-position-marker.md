# Plan: GPS User Position Marker on Map

**Date**: 2026-04-03
**Status**: Draft

## Summary

Display the device's current GPS position as a marker on the MapLibre map. The marker updates continuously as the user moves. A standard MapLibre `PointAnnotation` is used as the initial implementation, with a TODO left for a custom pulsing icon. Location permissions are already declared in the manifests and requested at runtime by `BlePermissionGuard`, so no new permission UI is required.

## Scope

**In scope:**
- Domain use case `ObserveUserLocationUseCase` that streams `GeoPoint` from `LocationRepository`
- `MainUiState` field `userLocation: GeoPoint?`
- `MainViewModel` collects location flow and updates state
- `MapLibreLayer` renders a `PointAnnotation` at the user's position
- DI wiring for the new use case
- TODO comment for custom icon replacement

**Out of scope:**
- Custom animated / pulsing icon (deferred, TODO left in code)
- "Center on me" button / camera auto-follow
- Accuracy circle around the marker
- Background location tracking
- Permission request UI changes (already handled by `BlePermissionGuard`)

## Architecture Notes

### Existing infrastructure (no new code needed)
- `LocationRepository` interface — `mesh/repository/LocationRepository.kt`
  - `getLocations(): Flow<Location>` (typealiased to `android.location.Location`)
  - `receivingLocationUpdates: StateFlow<Boolean>`
- `LocationRepositoryImpl` — 30 s update interval, high accuracy, `callbackFlow`
- Permissions already in both manifests; `BlePermissionGuard` already requests `ACCESS_FINE_LOCATION`
- Domain model `GeoPoint(latitude, longitude)` — `app/domain/marker/model/GeoPoint.kt`

### New code
| Layer | Artifact | Location |
|---|---|---|
| Domain | `ObserveUserLocationUseCase` | `app/domain/location/usecase/` |
| Presentation | `userLocation: GeoPoint?` in `MainUiState` | `app/presentation/feature/main/MainUiState.kt` |
| Presentation | location collection in `MainViewModel` | `app/presentation/feature/main/MainViewModel.kt` |
| Presentation | `PointAnnotation` in `MapLibreLayer` | `app/presentation/feature/main/osd/MapLibreLayer.kt` |
| DI | `ObserveUserLocationUseCase` binding | `app/di/LocationDomainModule.kt` (new, separate module — will grow) |

### Data flow
```
LocationRepositoryImpl (mesh)
  → getLocations(): Flow<android.location.Location>
  → ObserveUserLocationUseCase maps Location → GeoPoint
  → MainViewModel.collectAsState → MainUiState.userLocation
  → MapLibreLayer receives GeoPoint? param → PointAnnotation
```

### Use case design
```kotlin
class ObserveUserLocationUseCase(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): Flow<GeoPoint> =
        locationRepository.getLocations()
            .map { loc -> GeoPoint(loc.latitude, loc.longitude) }
}
```

### MapLibre marker (maplibre-compose 0.12.1)
Use `PointAnnotation` composable inside the `MaplibreMap` block:
```kotlin
// TODO: Replace standard pin with custom user-position icon (pulsing blue dot)
userLocation?.let { pos ->
    PointAnnotation(
        point = Point.fromLngLat(pos.longitude, pos.latitude),
    )
}
```

## Phase Plan

### Phase 0 — Research ✅ (completed before planning)
- **Goal**: understand existing location and map infrastructure
- **Findings**: `LocationRepository` fully implemented in `mesh`; permissions declared; `GeoPoint` model exists; `maplibre-compose:0.12.1` uses `PointAnnotation` for markers
- **Output**: this plan

### Phase 1 — Architecture Design
- **Goal**: approved architecture plan for new domain use case and ViewModel wiring
- **Tasks**:
  - Define `ObserveUserLocationUseCase` signature and its module placement
  - Decide DI module (`MapDataModule` extension vs. separate `LocationDomainModule`)
  - Confirm `MainUiState` shape with new `userLocation` field
- **Skill**: `/architect feature: ObserveUserLocationUseCase — wrap LocationRepository (mesh module) to expose Flow<GeoPoint>; wire into MainViewModel + MainUiState`
- **Output**: architecture decision doc or inline approval

### Phase 2 — UI Design
- **Goal**: confirm marker visual approach and decide icon placeholder
- **Tasks**:
  - Verify `PointAnnotation` API in `maplibre-compose:0.12.1` (check library source / docs)
  - Confirm TODO wording for custom icon
- **Skill**: direct code check (no new design tokens; skip `/ui-designer`)
- **Note**: Phase 2 is intentionally minimal — standard system marker, no custom asset yet

### Phase 3 — Implementation
- **Goal**: working GPS marker on map, buildable and runnable
- **Order** (domain → data → DI → presentation):
  1. Create `app/domain/location/usecase/ObserveUserLocationUseCase.kt`
  2. Add `userLocation: GeoPoint?` to `MainUiState`
  3. Create `app/di/LocationDomainModule.kt` — inject `LocationRepository` from mesh into `ObserveUserLocationUseCase`
  4. `MainViewModel`: inject use case, launch `collectLatest` in `viewModelScope`
  5. `MapLibreLayer`: add `userLocation: GeoPoint?` parameter and `PointAnnotation` block with TODO comment
  6. `MainScreen`: pass `uiState.userLocation` to `MapLibreLayer`
- **Skill**: direct coding (EnterPlanMode before starting)
- **Output**: committed, buildable code

### Phase 4 — Testing
- **Goal**: use case and ViewModel behaviour verified
- **Tasks**:
  - Unit test `ObserveUserLocationUseCase`: mock `LocationRepository`, assert `Flow<GeoPoint>` maps coordinates correctly
  - Unit test `MainViewModel`: mock use case, assert `uiState.userLocation` updates on emission
  - Manual smoke test: launch app, verify marker appears at device GPS position
- **Skill**: direct coding
- **Output**: passing test suite

### Phase 5 — Integration Review
- **Goal**: no Clean Architecture violations
- **Tasks**: review changed files — confirm no direct use of `LocationRepositoryImpl` from `app` layer; confirm ViewModel only depends on use case
- **Skill**: `/architect review: app/domain/location/, app/di/, app/presentation/feature/main/`
- **Output**: review report, violations fixed

### Phase 6 — Skill Update Review
- **Goal**: skills stay in sync with patterns established here
- **Tasks**:
  - `/architect` — add pattern: "mesh-layer repositories consumed in app-layer use cases via DI injection of the interface"
  - `/ui-designer` — no new design tokens or components → **no changes needed**
  - `/icon-designer` — no new icons → **no changes needed** (custom position icon is a future TODO)
  - `/planner` — no methodology gap found → **no changes needed**
- **Skill**: direct edit of `.claude/commands/architect.md` if pattern is new
- **Output**: updated architect skill (or explicit no-op for each skill)

## Coordination Map

```
Phase 0: [Explore agent — completed]
Phase 1: /architect feature: ObserveUserLocationUseCase + MainViewModel wiring
Phase 2: [direct code check — maplibre-compose PointAnnotation API]
Phase 3: [direct coding — EnterPlanMode]
Phase 4: [direct coding — unit tests + smoke test]
Phase 5: /architect review: app/domain/location/, app/di/, app/presentation/feature/main/
Phase 6: [skill update review — architect.md only if needed]
```

## Decisions

1. **DI module**: separate `app/di/LocationDomainModule.kt` — anticipated growth of location-related use cases.
2. **Update frequency**: 5 s (override `LocationRepositoryImpl` default of 30 s for map use).
3. **`receivingLocationUpdates` flag**: hide marker when `false`.
   - TODO left in code: design a proper "location unavailable" UX (e.g., grayed-out marker or snackbar).

## Open Questions

1. **`PointAnnotation` API**: confirm exact composable signature and icon override API in `maplibre-compose:0.12.1` before Phase 3.
2. **Update interval override**: `LocationRepositoryImpl` has a hardcoded 30 s interval — needs a parameter or a separate call site for 5 s. Resolve in Phase 1/3.

## Change Log

- 2026-04-03: created
- 2026-04-03: open questions resolved — separate LocationDomainModule, 5 s update interval, hide marker when location unavailable (TODO for UX)
