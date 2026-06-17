# GPS Background Service

## What it does
`GpsService` — Android foreground service that provides continuous GPS at 5 s intervals even when the app is minimized. Serves as the single GPS source for both the map marker and the mesh radio pipeline.

> **When the service starts and stops** — managed by `GpsServiceController`. See [`foreground-service-lifecycle.md`](foreground-service-lifecycle.md).

---

## Key classes

- `GpsService` — foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION`; `START_NOT_STICKY`; `service/`
- `GpsRepository` / `GpsRepositoryImpl` — `StateFlow<GpsLocation?>`, 5 s OS `LocationManager` subscription; `domain/gps/`, `data/gps/`
- `GpsLocation` — domain model (lat, lon, bearing, speed, accuracy, timestamp); `domain/gps/model/`
- `GpsLifecycleController` — interface to start/stop the `LocationManager` subscription; `domain/gps/repository/`
- `AppLocationProvider` — thin adapter: `GpsLocation` → MapLibre `Location`; `di/location/`
- `MeshLocationRepositoryAdapter` — implements mesh module's `LocationRepository`, bridges to `GpsRepository`; `data/gps/`
- `GpsModule` — Koin DI; `MeshLocationRepositoryAdapter` registered with `single(override=true)` to replace auto-scanned `LocationRepositoryImpl`
- `GpsServiceController` / `GpsServiceControllerImpl` — controls when `GpsService` runs; `domain/service/`, `data/service/`

---

## Non-obvious decisions

**Single OS subscription, two consumers**: `MeshLocationRepositoryAdapter` with `override=true` replaces `LocationRepositoryImpl` (mesh, 30 s) — one `LocationManager` call at 5 s feeds both the map and the radio GPS pipeline.

**Two separate lifecycles — service vs. GPS scanning**:
- `GpsService` (Android foreground service) lifecycle: controlled by `GpsServiceController` → starts on `nodeConnected` or track recording active, stops when both conditions false. Details in [`foreground-service-lifecycle.md`](foreground-service-lifecycle.md).
- `GpsLifecycleController` (LocationManager subscription) lifecycle: controlled by `BackgroundPositionSession` → starts when `shouldProvideNodeLocation=true` AND `geoAllowed=true`. Service can be running while GPS scanning is paused.

**Android 14+ FGS constraint**: `FOREGROUND_SERVICE_TYPE_LOCATION` cannot start from background. `startForegroundService` is called from `LaunchedEffect` inside `BlePermissionGuard` (Composable → Activity always in foreground when this runs). Details in [`foreground-service-lifecycle.md`](foreground-service-lifecycle.md).

**Swipe-from-Recents stops the service**: `onTaskRemoved() → stopSelf()`. `START_NOT_STICKY` means no auto-restart on OS kill — service comes back only on the next app open (via `GpsServiceController`).

**`isRunning` guard in `onStartCommand`**: `GpsServiceController` may call `startForegroundService` more than once if both `nodeConnected` and `trackRecording` trigger in sequence. Guard prevents double-init of `gpsLifecycle` and observers.

---

## Known limitations

- **TODO: добавить `altitude: Double?` в `GpsLocation`** — Android `Location.hasAltitude()` / `Location.altitude` доступны, но поле не добавлено. Нужно для отображения высоты над уровнем моря в шторке записи трека (`TrackRecordingSheet → TrackStatsSection`) и потенциально в `TrackPoint`.

---

## Source

Plan: `docs/archive/gps-background-service.md`  
Lifecycle refinement: `docs/archive/foreground-service-lifecycle.md`
