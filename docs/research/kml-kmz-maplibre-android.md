# Research: KML/KMZ as Map Sources in MapLibre Android

_Updated: clarified scope (markers + GroundOverlay), conversion strategy, OSMBonusPack compatibility_

---

## Findings

### Vector features (markers, polygons, lines)
- MapLibre has no native KML support — pipeline: **OSMBonusPack KML parse → GeoJSON string → `GeoJsonSource`**
- OSMBonusPack `KmlDocument.saveAsGeoJSON()` produces RFC 7946-compliant GeoJSON (correct lon/lat order, `FeatureCollection` structure)
- The legacy `"crs"` field appended by OSMBonusPack is silently ignored by MapLibre's C++ parser and `FeatureCollection.fromJson()` — no rejection occurs
- KML styles (`Style`, `StyleMap`) are **not** carried into GeoJSON — must be configured manually as MapLibre style layers
- Nested KML folders are flattened into a single `FeatureCollection`

### GroundOverlay (raster map underlay)
- OSMBonusPack **silently drops** GroundOverlay from GeoJSON output (`asGeoJSON()` returns `null`)
- Must be handled as a **separate pipeline**: OSMBonusPack parses `GroundOverlay` → `mIcon: Bitmap` + `LatLonBox` → **`ImageSource(id, LatLngQuad, bitmap)` + `RasterLayer`**
- `LatLngQuad` constructor: `(topLeft, topRight, bottomRight, bottomLeft)` — maps directly from KML `north/south/east/west`
- `ImageSource` accepts: `Bitmap` (direct), `file://` URI, `asset://` URI — all local sources supported
- OSMBonusPack already decodes the embedded image to `Bitmap` in `KmlGroundOverlay.mIcon` — no extra decoding needed
- Confirmed working pattern: `ImageSource` + `RasterLayer` in official MapLibre test app (`AnimatedImageSourceActivity.kt`)

### Conversion strategy: import-time (recommended)
- Parse KMZ once on import: unzip → KML parse → serialize GeoJSON to disk + save bitmap PNGs
- On map load: read cached GeoJSON file → feed to `GeoJsonSource`; load cached PNG → feed to `ImageSource`
- **Why import-time over runtime**: KMZ unzip + KML parsing is CPU-heavy for large files; avoids repeated parsing delay on every map open; cached GeoJSON is tiny; bitmaps cached as PNG avoid re-extraction

---

## Constraints for MeshTactics

- Two separate domain pipelines required: one for vector features (GeoJSON path), one for GroundOverlay (ImageSource path)
- KML style fidelity is partial — colors, icon URLs, stroke width must be re-applied via MapLibre layers
- KMZ-embedded icons for Placemarks need manual extraction + `map.addImage()` registration
- `NetworkLink`, `TimeSpan`, 3D elements — unsupported, will be silently dropped
- This is a **non-trivial domain layer task**: KMZ unzip → KML parse → GeoJSON serialize + bitmap extract → MapLibre source/layer setup

---

## Open Questions

- What MapLibre layer style should GroundOverlay use (opacity, blend mode)?
- Should multiple KML files be mergeable into one layer set, or always one file = one source?

---

## Sources

- OSMBonusPack GitHub: https://github.com/MKergall/osmbonuspack
  (`KmlDocument.java`, `KmlFolder.java`, `KmlGroundOverlay.java`)
- MapLibre Native Android — `ImageSource.kt`, `LatLngQuad.kt`, `RasterLayer.java`
- MapLibre test app — `AnimatedImageSourceActivity.kt` (confirmed LatLngQuad + RasterLayer pattern)
- MapLibre Style Spec: https://maplibre.org/maplibre-style-spec/layers/
- FME KML to GeoJSON guide: https://fme.safe.com/guides/spatial-computing/kml-to-geojson/
