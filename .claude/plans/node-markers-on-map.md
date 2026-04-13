# Plan: Node Markers on Map

**Date**: 2026-04-06
**Status**: Approved — branch synced 2026-04-06

## Summary

Display mesh node positions as markers on the MapLibre map. Each marker shows `longName` as a label above it. The "our node" marker is visually distinct from remote nodes. Only nodes with a valid GPS position are shown. Online/offline visual distinction is deferred to a follow-up task — in this iteration all positioned nodes look the same regardless of online status.

## Scope

**In scope:**
- Extend `MeshNodeModel` with `latitude: Double`, `longitude: Double`, `hasValidPosition: Boolean`, `isOnline: Boolean`
- Update `NodeMapper.toMeshNodeModel()` to map position and online status from `Node`
- New domain model `NodeMarkerModel` in `app/domain/marker/model/`
- New `ObserveNodeMarkersUseCase` in `app/domain/map/usecase/` — combines nodes + ourNode, filters by `hasValidPosition`, maps to `NodeMarkerModel`
- DI wiring for the new use case
- `MainUiState.nodeMarkers: List<NodeMarkerModel>`
- `MainViewModel` collects from the use case and updates state
- `MapLibreLayer` renders node markers with `longName` label
- Two icons: `ic_node_remote` and `ic_node_our`
- TODO: font size setting for node name labels
- TODO: tap behavior (popup, navigation to NodesScreen)
- TODO: inactive visual style for offline nodes (deferred feature)
- TODO: direction indicator for our node (future feature)

**Out of scope:**
- Visual distinction between online/offline nodes (deferred)
- Tap interactions beyond TODO comment
- Direction indicator / heading arrow for our node
- Font size configuration UI

## Architecture Notes

### Actual gps-user-position-marker implementation (post-merge)

The merged feature diverged from its original plan. Actual pattern (confirmed from code):

- **No `GeoPoint?` in `MainUiState`** — location is handled entirely inside `MapLibreLayer` via `locationProvider: LocationProvider`
- **No `ObserveUserLocationUseCase`** — `AppLocationProvider` wraps Android location and implements maplibre's `LocationProvider` interface
- **GeoJSON string + `CircleLayer`** — `PointAnnotation` was NOT used; the implementation manually builds a JSON string to bypass a crash in `spatialk:geojson:0.6.0` when serializing an empty `FeatureCollection()` via the polymorphic serializer
- **`locationDomainModule`** contains only `single<LocationProvider> { AppLocationProvider(...) }`
- **Current `MapLibreLayer` signature**: `(modifier, tileUrlTemplate, initialCameraPosition, onCameraPositionChanged, locationProvider: LocationProvider)`

### Consequence for node markers

Node markers **must use the same GeoJSON + Layer approach** (not `PointAnnotation`) to be consistent and avoid the same spatialk crash.

For text labels (`longName`): encode as a GeoJSON feature property, render via `SymbolLayer` with `textField` expression.
For two icon types: encode `isOurNode` as a boolean property; use a conditional icon expression in `SymbolLayer`, or two separate layers.

**Critical**: `SymbolLayer` text rendering requires a `glyphs` URL in the map style. `BaseStyle.Empty` does not have one — attempting to render text without it causes MapLibre native to break rendering of **all** layers, including `CircleLayer`. Solution: replace `BaseStyle.Empty` with a custom `BaseStyle.Json` that includes the `glyphs` field:
```kotlin
private val BASE_STYLE_WITH_GLYPHS = BaseStyle.Json(
    """{"version":8,"glyphs":"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf","sources":{},"layers":[]}"""
)
```
TODO: bundle fonts locally for offline use.

Nodes data is collected in `MainViewModel` → stored in `MainUiState.nodeMarkers` → passed as `List<NodeMarkerModel>` to `MapLibreLayer`. This is different from `locationProvider` (which is a singleton pulled inside the composable) because node data comes from a Flow and belongs to ViewModel state.

### Extended MeshNodeModel
```kotlin
data class MeshNodeModel(
    // ... existing fields ...
    val latitude: Double,
    val longitude: Double,
    val hasValidPosition: Boolean,
    val isOnline: Boolean,
)
```

### New NodeMarkerModel
```kotlin
// app/domain/marker/model/NodeMarkerModel.kt
data class NodeMarkerModel(
    val nodeId: String,
    val longName: String,
    val position: GeoPoint,
    val isOurNode: Boolean,
)
```

### New ObserveNodeMarkersUseCase
```kotlin
// app/domain/map/usecase/ObserveNodeMarkersUseCase.kt
class ObserveNodeMarkersUseCase(
    private val meshNetworkRepository: MeshNetworkRepository,
) {
    operator fun invoke(): Flow<List<NodeMarkerModel>> =
        combine(
            meshNetworkRepository.observeNodes(),
            meshNetworkRepository.observeOurNode(),
        ) { nodes, ourNode ->
            nodes
                .filter { it.hasValidPosition }
                .map { node ->
                    NodeMarkerModel(
                        nodeId = node.nodeId,
                        longName = node.longName,
                        position = GeoPoint(node.latitude, node.longitude),
                        isOurNode = node.nodeId == ourNode?.nodeId,
                    )
                }
        }
}
```

### GeoJSON encoding for MapLibreLayer
Node markers are encoded as a single GeoJSON string (same bypass pattern as user location dot):
```kotlin
val nodesGeoJson = remember(nodeMarkers) {
    val features = nodeMarkers.joinToString(",") { node ->
        val lon = node.position.longitude
        val lat = node.position.latitude
        val name = node.longName.replace("\"", "\\\"")
        val isOur = node.isOurNode
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"longName":"$name","isOurNode":$isOur}}"""
    }
    """{"type":"FeatureCollection","features":[$features]}"""
}
```

### Data flow
```
Node (mesh layer) — position.latitude_i / longitude_i, isOnline
  → NodeMapper: extended toMeshNodeModel()
  → MeshNodeModel (with lat/lon/hasValidPosition/isOnline)
  → ObserveNodeMarkersUseCase: combine(observeNodes, observeOurNode), filter, map
  → List<NodeMarkerModel>
  → MainViewModel.collectLatest → MainUiState.nodeMarkers
  → MapLibreLayer(nodeMarkers): builds GeoJSON string → GeoJsonSource → SymbolLayer
```

### MapLibreLayer final signature (this feature)
```kotlin
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
    nodeMarkers: List<NodeMarkerModel> = emptyList(),   // ← added by this feature
)
```

## Deferred: offline node visual state
Showing online/offline nodes identically in MVP. A follow-up task will:
- Add a dedicated inactive marker style
- Decide UX (greyed-out icon, reduced opacity, different shape)
This keeps the MVP scope focused and avoids premature design decisions.

## Phase Plan

### Phase 0 — Research ✅ (completed — branch synced 2026-04-06)
- **Goal**: understand maplibre-compose 0.12.1 API for markers with text labels; confirm post-merge state
- **Findings**:
  - `PointAnnotation` NOT used in current codebase — spatialk crash prevents it
  - Actual pattern: `GeoJsonData.JsonString` (raw JSON string) + `CircleLayer` / `SymbolLayer`
  - `SymbolLayer` supports `textField` expression for reading GeoJSON feature properties → use for `longName`
  - `locationProvider: LocationProvider` is the post-merge `MapLibreLayer` parameter for user location
  - Node data must flow through `MainUiState` (not via a singleton provider like `LocationProvider`)
- **Output**: Architecture Notes section above

### Phase 1 — Architecture Design
- **Goal**: approved architecture plan — models, interfaces, data flow, DI placement
- **Tasks**:
  - Confirm `MeshNodeModel` extension fields and mapper changes
  - Define `NodeMarkerModel` location and shape
  - Define `ObserveNodeMarkersUseCase` signature and DI module placement
  - Clarify: does `observeNodes()` include ourNode or not? (determines combine vs filter approach)
  - Confirm `MainUiState` shape with `nodeMarkers` field
- **Skill**: `/architect feature: node markers on map — ObserveNodeMarkersUseCase combines observeNodes + observeOurNode, filters by hasValidPosition, maps to NodeMarkerModel(isOurNode); MeshNodeModel extended with lat/lon/hasValidPosition/isOnline; MapLibreLayer receives List<NodeMarkerModel>`
- **Output**: architecture decision doc

### Phase 2 — UI / Icon Design ⏭ (deferred)
- **Decision**: skip custom icons for now — MVP uses `CircleLayer` with different colors per node type
  - Online node: Green 500 (`0xFF4CAF50`)
  - Offline node: Grey 500 (`0xFF9E9E9E`)
  - TODO: replace with custom icon sprites when `/icon-designer` phase is scheduled
- **Labels**: `SymbolLayer` with `textField = format(span(feature["longName"].asString()))` — implemented ✅
  - Online label: white text + black halo
  - Offline label: grey (`0xFFBDBDBD`) text + black halo
  - `textAnchor = Bottom`, `textOffset = offset(0f.em, -1.2f.em)` — label above circle
  - `textAllowOverlap = true` — always visible
  - `textSize = 12.sp`
  - TODO: typography token for node label text size

## Phase 3b — Show Stale Nodes as Grey Markers

**Goal:** Nodes with position older than 2 minutes appear on the map as grey markers (instead of being hidden).

### Changes Required

**1. `NodeMarkerModel` — add `isStale` field:**
```kotlin
data class NodeMarkerModel(
    val nodeId: String,
    val longName: String,
    val position: GeoPoint,
    val isOnline: Boolean,
    val isStale: Boolean,  // ← NEW: position older than 2 minutes
    val heading: Float?,
)
```

**2. `ObserveNodeMarkersUseCase` — remove freshness filter, add `isStale` flag:**
```kotlin
// BEFORE: filtered out stale nodes
val withFreshPosition = peers.filter {
    val effectiveTime = if (it.positionTime > 0) it.positionTime else it.lastHeard
    it.hasValidPosition && effectiveTime > freshnessThreshold
}

// AFTER: keep all positioned nodes, mark stale ones
val withPosition = peers.filter { it.hasValidPosition }.map { node ->
    val effectiveTime = if (node.positionTime > 0) node.positionTime else node.lastHeard
    val isStale = effectiveTime <= freshnessThreshold
    NodeMarkerModel(
        nodeId = node.nodeId,
        longName = node.longName,
        position = GeoPoint(node.latitude, node.longitude),
        isOnline = node.isOnline && !isStale,  // stale nodes treated as offline visually
        isStale = isStale,
        heading = if (node.groundSpeed > 0 && node.groundTrack > 0) node.groundTrack.toFloat() else null,
    )
}
```

**3. `MapLibreLayer` — add stale node layers:**

Current layer structure:
- `node-online-stationary` / `node-online-moving` (green icons)
- `node-remote-offline-dot` / offline labels (grey)

New structure — stale nodes get their own GeoJSON source + layers:
- Build **two** GeoJSON strings: one for fresh nodes, one for stale nodes
- OR: single GeoJSON with `isStale` property, use `filter` expressions in layers

**Option A: Single GeoJSON with filter (recommended)**
```kotlin
// In buildNodeGeoJson, add isStale property:
"""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},
   "properties":{"longName":"$name","isStale":$isStale,"bearing":...}}"""

// Add new layers for stale nodes (before online layers, so stale render underneath):
CircleLayer(
    sourceId = "peerNodesSource",
    layerId = "node-stale-dot",
    filter = feature.get("isStale").eq(true),
    circleColor = Color.StableColor(0xFF9E9E9E),  // grey
    circleRadius = ...
)
SymbolLayer(
    sourceId = "peerNodesSource",
    layerId = "node-stale-label",
    filter = feature.get("isStale").eq(true),
    textField = format(span(feature.get("longName"))),
    textColor = Color.StableColor(0xFF9E9E9E),  // grey text
    ...
)
```

**Option B: Two separate GeoJSON sources**
- `peerFreshSource` — fresh nodes (green/colored as before)
- `peerStaleSource` — stale nodes (grey)
- Simpler filter logic but more sources to manage

**Recommendation:** Option A (single source + filter) — less overhead, consistent with current offline layer pattern.

**4. `isOnline` semantics update:**
- For rendering purposes, stale nodes should be treated as offline (`isOnline = false` visually)
- The `isOnline` field in `NodeMarkerModel` currently drives color in existing layers
- Either: set `isOnline = false` for stale nodes in use case, OR add explicit `isStale` field and handle in layers
- **Decision:** Add `isStale` field, keep `isOnline` as-is (connectivity status), use `isStale` in layer filters

### Data Flow (updated)
```
MeshNodeModel (hasValidPosition, positionTime, lastHeard, isOnline)
  → ObserveNodeMarkersUseCase:
      - filter: hasValidPosition only
      - compute: isStale = effectiveTime <= freshnessThreshold
      - map: NodeMarkerModel(isStale, isOnline, heading, ...)
  → MainViewModel._uiState.nodeMarkers
  → MapLibreLayer:
      - GeoJSON includes isStale property
      - Layers: stale (grey circle + grey label) / offline (grey circle + grey label) / online (green circle + white label)
```

### Phase Plan

**Phase 3b.1 — Model Update**
- Add `isStale: Boolean` to `NodeMarkerModel`

**Phase 3b.2 — Use Case Update**
- Modify `ObserveNodeMarkersUseCase`: remove freshness filter, compute `isStale` for each node

**Phase 3b.3 — MapLibreLayer Update**
- Add `isStale` property to GeoJSON
- Add `CircleLayer` + `SymbolLayer` for stale nodes (grey styling)
- Layer ordering: stale → offline → online (bottom to top)

**Phase 3b.4 — Testing**
- Verify stale nodes appear grey on map
- Verify fresh nodes remain green
- Verify node transitions from fresh → stale → hidden (if goes offline entirely)

### Open Decisions
1. Should stale nodes use same icon as offline nodes, or a distinct visual (e.g., dashed circle)?
   - **Recommendation:** Same grey style as offline for simplicity; differentiate later if needed
2. Should there be a grace period (e.g., 2-5 min = yellow, 5+ min = grey)?
   - **Decision:** No — binary fresh/stale for MVP; can add渐变 later

---

### Phase 3 — Implementation ✅ (completed — 2026-04-10)
- **Goal**: working node markers on map, buildable and runnable
- **Prerequisite**: `gps-user-position-marker` branch merged ✅
- **Output**: committed, buildable, running code — circles + name labels confirmed on device

**Key implementation notes (actual vs planned):**
- `NodeMarkerModel` has `isOnline: Boolean` (not `isOurNode`) — our node is excluded by the use case
- Two `CircleLayer` + two `SymbolLayer` (online / offline split) instead of one layer with conditional icon
- `BaseStyle.Empty` replaced with `BASE_STYLE_WITH_GLYPHS` — `BaseStyle.Empty` has no `glyphs` URL; `SymbolLayer` text rendering requires it and without it MapLibre breaks all layer rendering (including `CircleLayer`)
  - Glyph URL: `https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf`
  - **TODO**: bundle fonts locally for offline use
- ~~`ObserveNodeMarkersUseCase` freshness filter: `POSITION_FRESHNESS_SECONDS = 2 * 60` (2 minutes)
  - Fallback: if `positionTime == 0` (firmware omits timestamp in Position packet), use `lastHeard`~~
  - **CHANGED (Phase 3b)**: stale nodes now shown as grey instead of filtered out
- TODO: font size setting for node name labels
- TODO: tap behavior
- TODO: direction icon for our node

### Phase 4 — Testing
- **Goal**: use case and ViewModel behaviour verified
- **Tasks**:
  - Unit test `ObserveNodeMarkersUseCase`:
    - Mock `MeshNetworkRepository`
    - Assert nodes without valid position are filtered out
    - Assert `isOurNode = true` for our node only
    - Assert `GeoPoint` coordinates match `lat/lon` from `MeshNodeModel`
  - Unit test `MainViewModel`:
    - Mock use case
    - Assert `uiState.nodeMarkers` updates on emission
  - Manual smoke test: launch app with connected mesh device, verify node markers appear at correct map positions
- **Skill**: direct coding
- **Output**: passing test suite

### Phase 5 — Integration Review
- **Goal**: no Clean Architecture violations
- **Tasks**:
  - Confirm `MapLibreLayer` depends only on `NodeMarkerModel`, not on `MeshNodeModel`
  - Confirm `MainViewModel` depends only on `ObserveNodeMarkersUseCase`, not on repository directly
  - Confirm `ObserveNodeMarkersUseCase` does not reference presentation types
- **Skill**: `/architect review: app/domain/marker/model/NodeMarkerModel.kt, app/domain/map/usecase/ObserveNodeMarkersUseCase.kt, app/presentation/feature/main/`
- **Output**: review report, violations fixed

### Phase 6 — Skill Update Review
- **Goal**: skills stay in sync with patterns established in this feature
- **Tasks**:
  - `/architect` — add "combinator use case" pattern: a use case that `combine()`s two repository flows and maps the result to a UI projection model. Placement: `domain/map/usecase/`. Also: MeshNodeModel extension pattern (adding projection fields to existing domain model).
  - `/ui-designer` — add typography token for node label if a new token was defined in Phase 2; otherwise no changes needed.
  - `/icon-designer` — document the two-variant node icon pattern (remote vs our) and the "TODO: directional future" convention for our-node icon.
  - `/planner` — check for methodology gaps found during planning.
- **Skill**: direct edit of `.claude/commands/<skill>.md`
- **Output**: updated skill files (or explicit "no changes needed" for each)

## Coordination Map

```
Phase 0: [Explore agent — completed]
Phase 1: /architect feature: ObserveNodeMarkersUseCase + MeshNodeModel extension + MapLibreLayer wiring — completed
Phase 2: ⏭ deferred — MVP uses CircleLayer (grey / green) instead of custom icons; labels done via SymbolLayer
Phase 3: [direct coding — completed 2026-04-10]
Phase 4: [direct coding — unit tests + smoke test]
Phase 5: /architect review: app/domain/marker/model/, app/domain/map/usecase/, app/presentation/feature/main/
Phase 6: [skill update review — architect, ui-designer, icon-designer, planner]
```

## Open Questions

1. **`PointAnnotation` vs GeoJSON** — ✅ Resolved: use GeoJSON + `SymbolLayer` (same pattern as user location dot).
2. **`MapLibreLayer` post-merge signature** — ✅ Resolved: `(modifier, tileUrlTemplate, initialCameraPosition, onCameraPositionChanged, locationProvider: LocationProvider)`.
3. **`ourNode in observeNodes()`**: Does `MeshNetworkRepository.observeNodes()` return our node in the list, or only remote nodes? If yes — `combine` approach must guard against producing a duplicate with `isOurNode = true`. Resolve in Phase 1 (architect).
4. **`SymbolLayer` icon loading**: Icons loaded via `SymbolLayer` in maplibre-compose require registering the drawable into the map style. Confirm the exact API in maplibre-compose 0.12.1 for image registration — may require `style.addImage(...)` call inside the `MaplibreMap` lambda.

## Change Log

- 2026-04-06: created
- 2026-04-06: updated — branch synced; Architecture Notes rewritten to match actual gps-user-position-marker implementation (GeoJSON + CircleLayer, no PointAnnotation, no GeoPoint in MainUiState); Phase 0 marked complete; Phase 3 steps revised to use GeoJSON + SymbolLayer; Open Questions updated
- 2026-04-10: Phase 3 completed — circles + labels confirmed working on device; key findings: BaseStyle.Empty incompatible with SymbolLayer (no glyphs URL), positionTime==0 fallback to lastHeard; Phase 2 labels section updated
