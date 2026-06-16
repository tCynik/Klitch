# GPS User Position Marker

## What it does
Displays the device GPS position as a bearing-aware navigation arrow on the map, updated at 5 s intervals from a foreground GPS service. Arrow rotates based on device orientation sensor and compensates for map camera rotation.

## Key classes
- `GpsService` — Android foreground service, manages GPS scope; `service/`
- `GpsRepository` / `GpsRepositoryImpl` — 5 s OS LocationManager subscription; `domain/gps/`, `data/gps/`
- `AppLocationProvider` — thin adapter: `GpsLocation` → MapLibre `Location`; `di/location/`
- `DeviceOrientationProvider` — `callbackFlow` on `TYPE_ROTATION_VECTOR`; `di/orientation/`
- `MeshLocationRepositoryAdapter` — bridges `GpsRepository` to mesh module's `LocationRepository` via Koin `override=true`; `data/gps/`
- Arrow rendering — Compose `Image` overlay in `MainScreen`, NOT a MapLibre SymbolLayer

## Non-obvious decisions
- **Compose overlay, not SymbolLayer**: maplibre-compose 0.12.1 has a name-shadowing bug — `SymbolLayer` data class shadows the `SymbolLayer` composable function, making `iconImage`/`iconRotate` inaccessible. Arrow is a `Modifier.rotate()` on a Compose `Image` instead.
- **Rotation formula**: `arrowRotation = deviceBearing + 180f - cameraBearing`. The `+180f` compensates for `ic_navigation_arrow.xml` pointing south by default; `-cameraBearing` keeps the arrow correct in heading-up mode.
- `CameraState` is created in `MainScreen` (not inside `MapLibreLayer`) and passed down — needed to share the projection with the overlay.
- **`MeshLocationRepositoryAdapter` with `override=true`**: replaces `LocationRepositoryImpl` (mesh, 30 s) — single OS subscription now feeds both the map display and the radio GPS pipeline.
- `spatialk:geojson:0.6.0` crashes on `FeatureCollection()` with no features (`firstNotNullOf` on empty list). Workaround: `GeoJsonData.JsonString(…)` bypass.
- `GpsService` stops on swipe-from-Recents (`onTaskRemoved → stopSelf()`), restarts on next `MainActivity.onCreate()`.

## Known limitations / planned extensions
- Migrate arrow to SymbolLayer when maplibre-compose fixes the name-shadowing bug (issue discussions #468, #533, #535, #658)
- Track recording stub in `TrackRecordingRepository` — implementation is a separate plan

## Source
Plans: `docs/archive/gps-user-position-marker.md`, `docs/archive/feature-user-location-arrow.md`, `docs/archive/gps-background-service.md`
