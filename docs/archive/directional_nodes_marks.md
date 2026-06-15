# Directional Node Markers — Implementation Plan

## Status: Done

---

## Concept

Nodes on the map indicate movement direction through marker shape.

| State | Shape | Layer |
|---|---|---|
| Offline | grey circle | CircleLayer (unchanged) |
| Online, no movement | diamond, all 4 corners rounded ≈ circle | SymbolLayer |
| Online, moving | diamond, top corner sharp, 3 corners rounded | SymbolLayer, `iconRotate = bearing` |

**Shape:** square rotated 45° (diamond/rhombus). Sharp corner points north at bearing=0° — no correction needed.

**Vector:** `VectorDrawable` → `toBitmap(width, height)` at the required DPI. `iconSize` in SymbolLayer controls scale via zoom-expression. Source remains a vector.

---

## Current Implementation (unchanged)

- `MapLibreLayer.kt`: CircleLayer for offline nodes (`node-remote-offline-dot`) — no changes
- Offline CircleLayer: color `0xFF9E9E9E`, radius 6dp, stroke white 1.5dp

---

## Drawable Geometry (canvas 80×80, content area [12,68])

Diamond: center (40,40), vertices T=(40,14), R=(66,40), B=(40,66), L=(14,40).
Rounded corner radius: r=12.

### `ic_node_marker_stationary.xml` — all 4 corners rounded

```
M 28.7,25.3
Q 40,14 51.3,25.3
L 57.5,31.5
Q 66,40 57.5,48.5
L 48.5,57.5
Q 40,66 31.5,57.5
L 22.5,48.5
Q 14,40 22.5,31.5
L 28.7,25.3 Z
```

### `ic_node_marker_moving.xml` — top corner sharp (bearing=0 → north)

```
M 40,14
L 57.5,31.5
Q 66,40 57.5,48.5
L 48.5,57.5
Q 40,66 31.5,57.5
L 22.5,48.5
Q 14,40 22.5,31.5
L 31.5,22.5
L 40,14 Z
```

Both files: `fillColor="#FF4CAF50"`, `strokeColor="#FFFFFFFF"`, `strokeWidth="3"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, viewport 80×80.

---

## Files Affected

- `domain/marker/model/NodeMarkerModel.kt` — add `heading: Float?`
- `domain/map/usecase/ObserveNodeMarkersUseCase.kt` — compute heading from `ground_track` + speed threshold
- `presentation/feature/main/osd/MapLibreLayer.kt` — replace online CircleLayer with two SymbolLayers
- `app/src/main/res/drawable/ic_node_marker_stationary.xml` — new file
- `app/src/main/res/drawable/ic_node_marker_moving.xml` — new file

---

## Implementation Phases

### Phase 1 — Model and Data
1. Add `heading: Float?` to `NodeMarkerModel`
2. In `ObserveNodeMarkersUseCase`: propagate `heading` from `MeshNodeModel`
   - Check availability of `groundTrack` / `groundSpeed` in the node model
   - Constant `MIN_SPEED_FOR_HEADING = 0.5f` (m/s) — below = stationary, `heading = null`
3. In GeoJSON builder (`buildNodeGeoJson`): add property `"bearing": heading ?: 0.0`

### Phase 2 — Drawable Files
4. Create `ic_node_marker_stationary.xml`
5. Create `ic_node_marker_moving.xml`

### Phase 3 — MapLibre Layers
6. Convert VectorDrawable → Bitmap in a `remember {}` block (accounting for screen density)
7. Register bitmaps: `style.addImage("node-online-stationary", bitmap)` and `"node-online-moving"`
8. Replace online CircleLayer (`node-remote-online-dot`) with two SymbolLayers:
   - `node-online-stationary`: filter `["!", ["has", "bearing_known"]]` or `bearing == null`
   - `node-online-moving`: filter `["has", "bearing_known"]`, `iconRotate = ["get", "bearing"]`
9. `iconRotationAlignment = "map"`, `iconAllowOverlap = true`
10. `iconSize` = zoom expression (linear, e.g. zoom 10→18 = 0.6→1.4)

### Phase 4 — Stationary/Moving Split in GeoJSON
11. Add boolean property `"bearing_known": true/false` to GeoJSON for layer filtering

---

## Key Decisions

- Offline nodes **never** have a direction → CircleLayer is not touched
- Stationary online node is shown as a "circle" (diamond with 4 rounded corners)
- Moving online node: sharp corner = movement direction, rotated by MapLibre via `iconRotate`
- When `heading = null` → show stationary icon (no rotation)
- Shape designed so bearing=0° aligns with north without additional correction
