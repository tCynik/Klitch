# Map Start Position

## What it does
On launch the map opens at the last saved camera position (lat/lon/zoom). On first launch falls back to a hardcoded Krasnoyarsk default. Saves on camera-idle and on Activity pause.

## Key classes
- `MapCameraPosition` — domain model (lat, lon, zoom as `Double`); `domain/map/model/`
- `LastMapPositionRepository` / `LastMapPositionRepositoryImpl` — persists via `multiplatform-settings`; `data/local/map/`
- `GetLastMapPositionUseCase`, `SaveLastMapPositionUseCase` — domain; `domain/map/usecase/`
- `MainViewModel` — reads on init, exposes `onCameraPositionChanged()`
- `MapLibreLayer` — fires `onCameraPositionChanged` callback via `LaunchedEffect(cameraState.isCameraMoving)`

## Non-obvious decisions
- `LastMapPositionRepositoryImpl` injects `Settings` directly, NOT through `AppSettings` wrapper — keeps concerns separated
- `CameraPosition.target = Position(longitude, latitude)` — **longitude first** (MapLibre convention)
- Spurious save guard: `isCameraMoving` is `false` on first composition; use `hasUserMoved` flag to skip the initial emission
- Strategy C (onPause save): `MainScreen` tracks `lastSavedPosition` via `remember`, updated by the camera-idle callback — avoids hoisting `cameraState` up

## Known limitations / planned extensions
- Default position hardcoded to Krasnoyarsk (56.0184, 92.8672) — TODO: replace with GPS first-fix on first launch

## Source
Plan: `docs/archive/map-start-position.md`
