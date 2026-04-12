# Plan: Background GPS Service

**Date**: 2026-04-10
**Status**: Done

## Summary

Creates `GpsService` — a single Android foreground service in the app module that provides continuous GPS even when the app is minimized. It becomes the unified GPS source for the user position marker on the map, and the future foundation for track recording and route navigation. GPS-to-radio (via `MeshService` + `AndroidMeshLocationManager`) is NOT changed — it already works in background.

## Scope

**In scope:**
- `GpsLocation` domain model + `GpsRepository` interface
- `GpsRepositoryImpl` — LocationManager subscription (replaces direct logic in `AppLocationProvider`)
- `GpsService` — Android foreground service, manages `GpsRepositoryImpl` scope
- `AppLocationProvider` refactored to read from `GpsRepository` (thin MapLibre adapter)
- AndroidManifest updates (service declaration + FOREGROUND_SERVICE_LOCATION permission)
- `MainActivity` starts `GpsService` on create

**Out of scope:**
- Track recording implementation (interface stub only, separate plan)
- Route navigation implementation (interface stub only, separate plan)
- GPS-to-radio changes (`MeshService`/`AndroidMeshLocationManager` already works in background)
- Unifying app-layer GPS with mesh-module `LocationRepositoryImpl` (two separate OS subscriptions is acceptable)

## Architecture Notes

### Current state
- `AppLocationProvider` (Koin `@Single`) has its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)` but `SharingStarted.WhileSubscribed()` — stops GPS when UI is backgrounded.
- `MeshService` (mesh module) is already a foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION`; `AndroidMeshLocationManager` sends GPS to radio in its scope — works in background.
- `LocationRepositoryImpl` (mesh module) — separate OS-level `LocationManager` subscription at 30s. Auto-registered by `@ComponentScan` in `CoreDataAndroidModule`.
- `mesh.repository.Location` is `typealias Location = android.location.Location` — trivial to map from domain `GpsLocation`.
- App manifest has `ACCESS_FINE_LOCATION` but NOT `FOREGROUND_SERVICE_LOCATION`.

### Target architecture

```
GpsService (Android Service, app module)
  → manages CoroutineScope for GpsRepositoryImpl
  → foreground with FOREGROUND_SERVICE_TYPE_LOCATION notification
  → START_STICKY restart policy
  → started from MainActivity.onCreate() via startForegroundService()
  → idempotent: repeated calls to startForegroundService() call onStartCommand() again, no conflict

GpsRepository interface (app/domain/gps/repository/)
  val location: StateFlow<GpsLocation?>
  val isReceivingUpdates: StateFlow<Boolean>

GpsRepositoryImpl (app/data/gps/)
  → single OS subscriber: LocationManager (FusedProvider/GPS/Network fallback)
  → interval: 5 000 ms
  → StateFlow<GpsLocation?> with SharingStarted.Eagerly in service scope

GpsLocation data class (app/domain/gps/model/)
  → lat, lon, bearing, speed, accuracy, timestamp
  → domain model, no MapLibre or Android Location types

AppLocationProvider (app/di/location/)
  → reads from GpsRepository
  → maps GpsLocation → MapLibre Location
  → no direct LocationManager access

MeshLocationRepositoryAdapter (app/data/gps/)
  → implements mesh.repository.LocationRepository
  → getLocations(): gpsRepository.location.filterNotNull().map { it.toMeshLocation() }
  → receivingLocationUpdates: delegates to gpsRepository.isReceivingUpdates
  → registered in DI with override=true — replaces LocationRepositoryImpl

// TODO (track-recording plan): TrackRecordingRepository interface stub in app/domain/tracking/
// TODO (track-recording plan): GpsService.startTrackRecording(scope) — coroutine launched here,
//   subscribes to GpsRepository, buffers points in SQLDelight, survives service restart
```

### Unification strategy (two OS subscribers → one)
- `LocationRepositoryImpl` (mesh, 30s) is replaced by `MeshLocationRepositoryAdapter` (app) — reads from `GpsRepositoryImpl` (5s)
- `MeshLocationRepositoryAdapter` uses Koin `single<LocationRepository>(override = true)` to replace the auto-scanned binding
- `LocationRepositoryImpl` class remains in mesh module but is no longer the active binding
- `AndroidMeshLocationManager` now receives GPS at 5s (acceptable — previously "no throttling, acceptable for MVP" per phone-gps-to-radio plan)
- Net result: one OS `LocationManager` subscription, two consumers (map + mesh radio)

### Track recording: architecture stub
Track recording is NOT implemented in this plan, but the structure must support it:
- `TrackRecordingRepository` interface stub created in `app/domain/tracking/`
- `GpsService` has a private `startTrackRecording(scope)` stub with TODO
- Buffer persistence: SQLDelight — to be designed in the track-recording plan
- The service scope in `GpsService` is the natural host for the recording coroutine (survives app backgrounding)

### Service stop mechanism

Three stop triggers — different behaviour for each:

| Trigger | How | Result |
|---|---|---|
| Home / switch tasks / screen off | — | Service keeps running (`START_STICKY`) |
| OS kills service (low memory) | — | `START_STICKY` → auto-restart |
| Swipe from Recents | `onTaskRemoved()` → `stopSelf()` | Clean stop, no restart |
| Close button (future feature) | `stopService(intent)` from Activity | Clean stop, no restart |

`stopSelf()` is a clean stop — `START_STICKY` does NOT re-schedule restart for clean stops, only for kills. The service is brought back on next `MainActivity.onCreate()`.

**Asymmetry with MeshService**: `MeshService.onTaskRemoved` only logs — BLE connection intentionally survives swipe-close. `GpsService` stops on swipe-close — GPS tracking without a user makes no sense.

**Future close-button feature note**: the close button must explicitly stop BOTH services:
```
stopService(GpsService.createIntent(this))
stopService(MeshService.createIntent(this))   // only if BLE disconnect on close is desired
finishAndRemoveTask()
```
This should be captured in the close-button feature plan, not here.

### Android 14+ constraint
FOREGROUND_SERVICE_TYPE_LOCATION cannot be started from background on Android 14+.
Mitigation: start `GpsService` from `MainActivity.onCreate()` (foreground context).
On process kill → `START_STICKY` → system restarts service; first frame after user returns to app triggers Activity (foreground context) — acceptable.

### Permissions
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` — add to app `AndroidManifest.xml`
- `ACCESS_BACKGROUND_LOCATION` — NOT needed (foreground service exempts from this requirement)

## Phase Plan

### Phase 0 — Research (skip)
Domain well understood. Android foreground service + location type constraints known.

### Phase 1 — Architecture Design
**Goal**: approved architecture, scaffolded interfaces
**Tasks**:
- Domain model: `GpsLocation`, `GpsRepository`
- Data: `GpsRepositoryImpl` structure + `GpsService` class outline
- DI: `GpsModule` Koin binding strategy
- Review `AppLocationProvider` adapter pattern
**Skill**: `/architect feature: GpsService — app-layer foreground GPS service, replaces AppLocationProvider as GPS source; domain GpsRepository/GpsLocation, GpsRepositoryImpl manages LocationManager, GpsService holds service scope, AppLocationProvider becomes thin MapLibre adapter`
**Output**: architecture plan, scaffolded files

> **Token checkpoint**: run `/compact` before Phase 3

### Phase 2 — UI / Icon Design (skip)
No new UI elements. Foreground notification uses existing `ic_triangle_arrow`.

### Phase 3 — Implementation
**Goal**: working `GpsService`, `AppLocationProvider` on new source, single OS subscription
**Order**: domain → data → DI → manifest → presentation
**Files to create/modify**:
1. `app/src/main/java/.../domain/gps/model/GpsLocation.kt` — new
2. `app/src/main/java/.../domain/gps/repository/GpsRepository.kt` — new
3. `app/src/main/java/.../domain/tracking/repository/TrackRecordingRepository.kt` — stub interface, TODO comment
4. `app/src/main/java/.../data/gps/GpsRepositoryImpl.kt` — new (port logic from `AppLocationProvider`)
5. `app/src/main/java/.../data/gps/MeshLocationRepositoryAdapter.kt` — new (implements mesh `LocationRepository`, maps from `GpsRepository`)
6. `app/src/main/java/.../service/GpsService.kt` — new (foreground service; TODO stub for `startTrackRecording`)
7. `app/src/main/java/.../di/GpsModule.kt` — new (`GpsRepository`, `MeshLocationRepositoryAdapter` with `override=true`)
8. `app/src/main/java/.../di/location/AppLocationProvider.kt` — refactor to read from `GpsRepository`
9. `app/src/main/AndroidManifest.xml` — add service + FOREGROUND_SERVICE + FOREGROUND_SERVICE_LOCATION
10. `app/src/main/java/.../MainActivity.kt` — `startForegroundService(GpsService.createIntent(this))`
**Skill**: direct coding (EnterPlanMode before starting)
**Post-implementation**: `/simplify` on changed files
**Output**: buildable code, single OS subscription

### Phase 4 — Testing
**Goal**: GPS continuity in background verified
**Tasks**:
- Unit: `GpsRepositoryImpl` — mock `LocationManager`, verify `StateFlow` updates
- Manual checklist:
  1. Open app → user position marker appears
  2. Minimize app for 30s → return → marker position is current (no delay/jump)
  3. Logcat: "GpsService started" persists after home button
**Skill**: direct coding
**Output**: unit test + manual checklist

### Phase 5 — Integration Review
**Goal**: no architectural violations
**Tasks**: confirm `AppLocationProvider` has no direct `LocationManager`; `GpsService` has no domain use case calls; DI module is self-contained
**Skill**: `/architect review: GpsService, GpsRepositoryImpl, AppLocationProvider, GpsModule`
**Output**: review report, violations fixed

### Phase 6 — Skill Update Review
- **`/architect`**: new pattern — Android foreground service in app layer (GPS), DI scope strategy for service-bound singletons; AppLocationProvider as thin MapLibre adapter pattern
- **`/ui-designer`**: no changes
- **`/icon-designer`**: no changes
- **`/planner`**: no changes

### Phase 6b — Docs & Memory Update
- Update `CLAUDE.md` feature table: GPS Background Service → Done
- Set this plan status to `Done`
- Update `memory/project_state.md`
- Record token cost in Change Log

### Phase 7 — Commit Preparation
Stage files by name after Phase 5+6+6b complete. Propose commit message. Wait for user confirmation.

## Coordination Map

```
Phase 0: [skip]
Phase 1: /architect feature: GpsService ... → [/compact]
Phase 2: [skip]
Phase 3: [direct coding — domain → data → DI → manifest → presentation] → /simplify
Phase 4: [direct coding — tests + manual checklist]
Phase 5: /architect review: GpsService, GpsRepositoryImpl, AppLocationProvider, GpsModule
Phase 6: [skill update review]
Phase 6b: [docs & memory — CLAUDE.md, plan file, memory/]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Open Questions

1. ~~GpsService start point~~ — `MainActivity.onCreate()` confirmed.
2. ~~GPS interval~~ — 5 000 ms confirmed.
3. ~~Dual LocationManager subscriptions~~ — resolved: `MeshLocationRepositoryAdapter` bridges mesh module to `GpsRepositoryImpl`; one OS subscription.
4. ~~Track recording scope~~ — stub only in this plan, full implementation in separate plan. `GpsService` scope is the designated host for the recording coroutine.

## Change Log

- 2026-04-10: created
- 2026-04-12: implementation complete — GpsLifecycleController split interface added (Phase 5 architect review fix), GpsModule uses `binds arrayOf(...)` + `override = true`, unit tests written (JDK 25 infra issue blocks run), architect.md updated with Foreground Service Lifecycle Pattern + 2 new anti-patterns. Status → Done.
