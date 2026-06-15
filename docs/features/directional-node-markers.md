# Directional Node Markers

## What it does
Online mesh nodes are rendered as diamond markers on the map — stationary nodes show a rounded diamond, moving nodes show a sharp-top diamond rotated to face their heading. Offline nodes stay as grey circles.

## Key classes
- `NodeMarkerModel` — domain model; added `heading: Float?`; `domain/marker/model/`
- `ObserveNodeMarkersUseCase` — computes `heading` from `groundTrack` + speed threshold; `domain/map/usecase/`
- `MapLibreLayer` — two `SymbolLayer`s for online nodes, `CircleLayer` for offline; `presentation/feature/main/osd/`
- `ic_node_marker_stationary.xml` — diamond, 4 rounded corners; `drawable/`
- `ic_node_marker_moving.xml` — diamond, top corner sharp (points north at bearing=0); `drawable/`

## Non-obvious decisions
- **Shape designed for zero-correction**: sharp corner at top (T vertex = `40,14`) → bearing=0° aligns with north automatically; no `iconOffset` needed
- **`bearing_known` GeoJSON property**: boolean property added to each node's GeoJSON feature to filter the two SymbolLayers (`["has", "bearing_known"]` vs `["!", ["has", "bearing_known"]]`)
- `MIN_SPEED_FOR_HEADING = 0.5f` m/s — below this threshold `heading = null` → stationary icon shown
- Offline CircleLayer (`node-remote-offline-dot`) is **not touched** — only online nodes get SymbolLayer treatment
- VectorDrawable → Bitmap conversion happens in a `remember {}` block at composable startup (accounts for screen density)

## Known limitations / planned extensions
- HEADING and SPEED `position_flags` must be enabled in firmware for `heading` to arrive in telemetry packets (see LocationConfig settings)
- Online/offline visual distinction is implemented; signal strength colour-coding is deferred

## Source
Plan: `docs/archive/directional_nodes_marks.md`
