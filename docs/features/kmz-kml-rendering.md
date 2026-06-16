# KMZ/KML Overlay Rendering

## What it does
Renders selected KMZ/KML overlays on the MapLibre map — vector geometry (Placemarks, lines, polygons) via GeoJSON sources and raster ground overlays via `ImageSource`. Toggled per-overlay via the checkbox in Settings → Map tab.

## Key classes
- `KmlOverlayParser` — parses KMZ/KML at import time, caches results to `filesDir/overlays/{id}/`; `data/local/map/`
- `ObserveSelectedOverlaysUseCase` — reads cached GeoJSON + bitmap from disk for selected overlays; `domain/map/usecase/`
- `OverlayRenderModel` / `GroundOverlayBounds` — presentation models; `presentation/feature/main/osd/models/`
- `MainUiState.selectedOverlays: ImmutableList<OverlayRenderModel>` — drives `MapLibreLayer`
- `MapLibreLayer` — renders: `rememberImageSource(PositionQuad, bitmap)` for raster; `rememberGeoJsonSource` + `FillLayer`/`LineLayer`/`SymbolLayer` for vector

## Non-obvious decisions
- **Parse on import, not on render**: KMZ/KML parsing is CPU-heavy; results are cached as `features.geojson` + `ground_overlay.png` + `ground_overlay_bounds.json` in `filesDir/overlays/{id}/`. Map startup reads cached files only.
- **Bounds as JSON file** (not SQLDelight columns): keeps schema simple; read together with the bitmap in `ObserveSelectedOverlaysUseCase`.
- `rememberImageSource(PositionQuad, ImageBitmap)` exists in maplibre-compose 0.12.1 — no `MapEffect` escape hatch needed.
- `PositionQuad(topLeft, topRight, bottomRight, bottomLeft)` uses `org.maplibre.spatialk.geojson.Position` — counterclockwise from top-left.
- Layer order: base raster → ground overlays → fill → line → symbol → node markers.
- `KmlDocument.saveAsGeoJSON()` from OSMBonusPack (6.9.0) handles Placemark → GeoJSON serialization.

## Known limitations / planned extensions
- No support for `NetworkLink`, `TimeSpan`, 3D KML elements
- KML stylization is basic (colors from KML, no Placemark icon overrides)
- Already-imported files are not retroactively reparsed when the schema changes

## Source
Plan: `docs/archive/kmz-kml-rendering.md`
