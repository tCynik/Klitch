# GPS Background Service

## What it does
`GpsService` — Android foreground service that provides continuous GPS at 5 s intervals even when the app is minimized. Serves as the single GPS source for both the map marker and the mesh radio pipeline.

## Key classes
- `GpsService` — foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION`; `START_STICKY`; `service/`
- `GpsRepository` / `GpsRepositoryImpl` — `StateFlow<GpsLocation?>`, 5 s OS `LocationManager` subscription; `domain/gps/`, `data/gps/`
- `GpsLocation` — domain model (lat, lon, bearing, speed, accuracy, timestamp); `domain/gps/model/`
- `AppLocationProvider` — thin adapter: `GpsLocation` → MapLibre `Location`; `di/location/`
- `MeshLocationRepositoryAdapter` — implements mesh module's `LocationRepository`, bridges to `GpsRepository`; `data/gps/`
- `GpsModule` — Koin DI; `MeshLocationRepositoryAdapter` registered with `single(override=true)` to replace auto-scanned `LocationRepositoryImpl`

## Non-obvious decisions
- **Single OS subscription, two consumers**: `MeshLocationRepositoryAdapter` with `override=true` replaces `LocationRepositoryImpl` (mesh, 30 s) — one `LocationManager` call at 5 s feeds both the map and the radio GPS pipeline
- **Android 14+ constraint**: `FOREGROUND_SERVICE_TYPE_LOCATION` cannot start from background. Service is started in `MainActivity.onCreate()` (foreground context). After OS kill, `START_STICKY` auto-restarts; the user returning to the app triggers the Activity (foreground) context.
- **Swipe-from-Recents stops the service**: `onTaskRemoved() → stopSelf()`. `START_STICKY` only restarts on OS kill, not on clean stop. Service comes back on next `MainActivity.onCreate()`. GPS tracking without an active user doesn't make sense.
- `GpsService` scope hosts the GPS flow — it's the designated host for future track recording coroutines (survives backgrounding).
- **GPS lifecycle tied to mesh session**: `BackgroundPositionSession` calls `GpsLifecycleController.start()` when `shouldProvideNodeLocation=true` + `geoAllowed=true`. GPS starts/stops together with the mesh geo-bridge, not independently.

## Known limitations / planned extensions
- Track recording: `TrackRecordingRepository` interface stub created; full implementation is a separate plan
- **TODO: добавить `altitude: Double?` в `GpsLocation`** — Android `Location.hasAltitude()` / `Location.altitude` доступны, но поле не добавлено. Нужно для отображения высоты над уровнем моря в шторке записи трека (`TrackRecordingSheet → TrackStatsSection`) и потенциально в `TrackPoint`.

## Source
Plan: `.claude/archive/gps-background-service.md`
