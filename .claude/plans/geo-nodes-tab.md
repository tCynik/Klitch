# Plan: Geo Nodes Tab (replaces Log Tab)

**Date**: 2026-04-11
**Status**: Approved

## Summary

Replace the Log tab in MeshTestScreen with a list of peer nodes that report geo data.
Each row shows the node's short name, distance from our position, and time elapsed since
the last position report. The tab slot, state, ViewModel logic, and composable all change;
existing log-related code is deleted.

## Scope

- In scope:
  - New domain model `GeoNodeModel` (shortName, distanceMeters, positionTime)
  - New use case `ObserveGeoNodesUseCase` (combine nodes + ourNode, filter hasValidPosition)
  - Replace `LogTabState` → `GeoNodesTabState` + `GeoNodeUi`
  - Replace `MeshTestTab.Log("Log")` → `MeshTestTab.GeoNodes("Nodes")`
  - Replace `LogTab.kt` composable → `GeoNodesTab.kt`
  - Remove log-related ViewModel methods and state (`onLogFilterChange`, `onLogPauseToggle`,
    `frozenLogEntries`, `applyLogFilter`, `ObservePacketLogUseCase` subscription)
  - Remove `LogTabState.kt`, `LogTab.kt`
  - DI wiring for new use case
- Out of scope:
  - Sorting or filtering controls in the tab UI
  - Navigation to a node detail screen
  - Freshness cutoff (all nodes with `hasValidPosition == true` are shown)
  - Removing `ObservePacketLogUseCase` from DI module if it becomes unused elsewhere

## Architecture Notes

### New domain model
`domain/mesh/model/GeoNodeModel.kt`
```kotlin
data class GeoNodeModel(
    val nodeId: String,
    val shortName: String,
    val distanceMeters: Int?,   // null when our position is unknown
    val positionTime: Int,       // Unix seconds of last GPS report
)
```

### New use case
`domain/mesh/usecase/ObserveGeoNodesUseCase.kt`
- Combines `repository.observeNodes()` + `repository.observeOurNode()`
- Filter: `node.hasValidPosition == true && node.nodeId != ourNode?.nodeId`
- Distance: `latLongToMeter()` from `mesh.common.util`; null when our node has no valid position
- Sort: by `positionTime` descending (newest first)
- Returns `List<GeoNodeModel>`

Pattern mirrors `ObserveNodeMarkersUseCase` — same combine pattern, same utility function.

### Presentation state
`state/GeoNodesTabState.kt`  — replaces `LogTabState.kt`
```kotlin
data class GeoNodesTabState(
    val nodes: ImmutableList<GeoNodeUi> = persistentListOf()
)

// in models/GeoNodeUi.kt
data class GeoNodeUi(
    val nodeId: String,
    val shortName: String,
    val distanceFormatted: String, // e.g. "1.2 km", "340 m", "—"
    val positionTime: Int,          // raw Unix seconds — composable computes age via ticker
)
```

### ViewModel changes
- Remove: `observePacketLog` subscription, `frozenLogEntries`, `onLogFilterChange`,
  `onLogPauseToggle`, `applyLogFilter`, `LogFilter`/`LogDirection`/`LogEntryUi` imports
- Add: inject `ObserveGeoNodesUseCase`, subscribe in `init`, map to `GeoNodesTabState`
- Use `DateUtils.getRelativeTimeSpanString` for positionAge (already imported)

### Composable
`components/tabs/GeoNodesTab.kt` — replaces `LogTab.kt`
- Seconds ticker via `LaunchedEffect(Unit) { while(true) { delay(1000); nowSeconds = ... } }`
- Simple `LazyColumn` of rows; each row receives `nowSeconds` and computes age string:
  - age < 60s → "${age}s"
  - age ≥ 60s → "${age / 60} min"
- Each row: shortName (left, bold), distanceFormatted (right), age (secondary below)
- Empty state: "No nodes with geo data"

## Phase Plan

### Phase 1 — Domain
1. Create `GeoNodeModel.kt` in `domain/mesh/model/`
2. Create `ObserveGeoNodesUseCase.kt` in `domain/mesh/usecase/`
3. Wire use case in DI module

### Phase 2 — Presentation State
4. Create `state/models/GeoNodeUi.kt`
5. Create `state/GeoNodesTabState.kt`
6. Replace `logTab: LogTabState` → `geoNodesTab: GeoNodesTabState` in `MeshTestUiState`

### Phase 3 — ViewModel
7. Remove log-related fields, subscriptions, and methods
8. Inject `ObserveGeoNodesUseCase`, subscribe, map to `GeoNodesTabState`
   - Formatting: distance via helper, age via `DateUtils.getRelativeTimeSpanString`

### Phase 4 — Composable & Tab Wiring
9. Delete `LogTab.kt`
10. Create `GeoNodesTab.kt`
11. Rename `MeshTestTab.Log("Log")` → `MeshTestTab.GeoNodes("Nodes")`
12. Update `MeshTestScreen`: replace `LogTab(...)` call with `GeoNodesTab(...)`
13. Delete `LogTabState.kt`

### Phase 5 — Simplify
- Run `/simplify` on changed files

### Phase 6 — Architecture Review
- `/architect review:` on new use case and ViewModel changes

### Phase 7 — Skill Update Review
- Check `/architect`, `/ui-designer`, `/planner` for updates needed

### Phase 7b — Docs & Memory
- Update CLAUDE.md (new entry for this feature)
- Update memory/project_state.md

### Phase 8 — Commit

## Coordination Map

```
Phase 1–4: [direct coding]
Phase 5:   /simplify
Phase 6:   /architect review: ObserveGeoNodesUseCase, MeshTestViewModel
Phase 7:   [skill update review]
Phase 7b:  [docs & memory — CLAUDE.md, memory/]
Phase 8:   [stage by name] → [propose commit] → [wait] → git commit
```

## Open Questions

- Should `GeoNodeUi.positionAgeFormatted` use a ticker (update every minute) or only refresh
  on new node data? → Start with data-driven refresh only; ticker is out of scope for MVP.
- All nodes with `hasValidPosition == true` are shown; no freshness cutoff.
- Stale nodes visible with their age — no visual warning for now.

## Change Log
- 2026-04-11: created
