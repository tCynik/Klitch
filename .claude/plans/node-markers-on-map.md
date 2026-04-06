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
  - Remote node: Grey 500 (`0xFF9E9E9E`)
  - Our node: Green 500 (`0xFF4CAF50`)
  - TODO: replace with custom icon sprites when `/icon-designer` phase is scheduled
  - TODO: typography token for node label text size (currently no label — SymbolLayer API TBD)

### Phase 3 — Implementation
- **Goal**: working node markers on map, buildable and runnable
- **Prerequisite**: `gps-user-position-marker` branch merged ✅
- **Order** (domain → data → DI → presentation):
  1. `MeshNodeModel` — add `latitude: Double`, `longitude: Double`, `hasValidPosition: Boolean`, `isOnline: Boolean`
  2. `NodeMapper.toMeshNodeModel()` — map `node.latitude`, `node.longitude`, `node.isOnline`, `node.validPosition != null`
  3. `NodeMarkerModel` — new file `app/domain/marker/model/NodeMarkerModel.kt`
  4. `ObserveNodeMarkersUseCase` — new file `app/domain/map/usecase/ObserveNodeMarkersUseCase.kt`; uses `combine(observeNodes, observeOurNode)`
  5. DI — register `ObserveNodeMarkersUseCase` in `meshDataModule` (alongside other mesh use cases)
  6. `MainUiState` — add `nodeMarkers: List<NodeMarkerModel> = emptyList()`
  7. `MainViewModel` — inject `ObserveNodeMarkersUseCase`, `collectLatest` in `viewModelScope`, update state
  8. `MapLibreLayer` — add `nodeMarkers: List<NodeMarkerModel> = emptyList()` param:
     - Build GeoJSON string (same raw-string pattern as user location, see Architecture Notes)
     - `rememberGeoJsonSource(GeoJsonData.JsonString(nodesGeoJson))`
     - `SymbolLayer` with `textField` from `longName` property
     - Icon: `ic_node_remote.xml` / `ic_node_our.xml` based on `isOurNode` property (conditional expression or two layers)
     - TODO: font size setting for node name labels
     - TODO: tap behavior
     - TODO: direction icon for our node
  9. `MainScreen` — add `nodeMarkers = uiState.nodeMarkers` to `MapLibreLayer` call
- **Skill**: direct coding (EnterPlanMode before starting)
- **Output**: committed, buildable code

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
Phase 2: ⏭ deferred — MVP uses CircleLayer (grey / green) instead of custom icons
Phase 3: [direct coding — EnterPlanMode]
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
