# Plan: HUD Node Status Indicator

**Date**: 2026-04-06
**Status**: Done

## Summary

Replace the DEBUG-only MeshTest `IconButton` in the HUD right column with a persistent node status
indicator. The indicator shows `--` when no node is selected/connected, or the count of mesh nodes
that have reported a valid geoposition when connected. The count is colored red on low BLE RSSI,
green on medium/high RSSI. This gives the operator an at-a-glance view of mesh awareness without
requiring any new domain use cases.

## Scope

**In scope:**
- Fix `ObserveNodeMarkersUseCase`: exclude our node entirely (remove the guard that adds `ourNode`
  to `allNodes`). Add a doc comment explaining the "our node is not a peer" design decision.
- Remove `isOurNode` field from `NodeMarkerModel` (invariant: all entries are peer nodes)
- Add `connectionStatus: MeshConnectionStatus` to `MainUiState`
- Subscribe to `ObserveConnectionStatusUseCase` in `MainViewModel`
- Derive indicator display state (text + color) from connection status + `nodeMarkers.size`
- Replace `IconButton(onMeshTestClick)` with `NodeStatusIndicator` composable in `HudControlsLayer`
- Remove `onMeshTestClick` parameter from `HudControlsLayer` and `MainScreen` (DEBUG button retired)
- Extract `RSSI_LOW_THRESHOLD = -90` as a named constant in presentation layer
- Add "our node" design decision to CLAUDE.md as a project-wide rule

**Out of scope:**
- Tapping the indicator (no action yet)
- Showing RSSI value numerically
- Filtering "stale" positions by timestamp (future feature)
- Counting nodes without position data
- Adding `[OWN NODE]` labels to data-layer logs (noted, tracked as a follow-up task)

## Architecture Notes

### Design Decision: Our Node Is Not a Network Peer

The user's radio device (our node) is part of the user's equipment — not a peer in the mesh
network. On the map, the user's position is already shown via a separate GPS/location layer
(CircleLayer). Including our node as a mesh node marker would create visual duplication — two
markers at the same location.

**Rule**: `ObserveNodeMarkersUseCase` returns **only peer nodes**. Our node is excluded at the
domain use case level. This is not a UI filter — it is a business rule.

Consequences:
- `NodeMarkerModel.isOurNode` field is removed — by definition, every entry in the returned list
  is a peer node, not our own
- The indicator count is simply `nodeMarkers.size` — no client-side filtering needed
- Any future use case dealing with peer nodes must apply the same exclusion

### RSSI Threshold

`RSSI_LOW_THRESHOLD = -90` dBm (BLE link to our radio, standard Meshtastic convention).
Extracted as a named constant in the presentation layer so it can be adjusted without touching
domain logic.

### All required domain pieces already exist:
- `ObserveConnectionStatusUseCase` → `Flow<MeshConnectionStatus>`
- `MeshConnectionStatus.Connected(nodeId, rssi, batteryLevel)` carries the BLE RSSI
- `nodeMarkers` in `MainUiState` after the fix: only peer nodes with valid position

### Indicator logic (pure, no new use cases):
```
display = when (connectionStatus) {
    is Disconnected, Scanning, Connecting, DeviceSleep, Error -> "--"
    is Connected -> nodeMarkers.size.toString()
}

color = when {
    connectionStatus !is Connected -> Color.Gray
    connectionStatus.rssi < RSSI_LOW_THRESHOLD -> Color.Red
    else -> Color.Green
}
```

## Phase Plan

### Phase 0 — Research ✅ DONE
- Findings documented above. No unknowns remain.

### Phase 1 — Implementation

**Goal**: Working indicator on screen, DEBUG button removed, our node excluded from map.

**Tasks (in order):**

1. **`NodeMarkerModel.kt`** — remove `isOurNode: Boolean` field.

2. **`ObserveNodeMarkersUseCase.kt`** — exclude our node from the peer list:
   - Remove the guard block that adds `ourNode` to `allNodes` when not present
   - Filter out `ourNode` from `nodes` list before mapping: `nodes.filter { it.nodeId != ourNodeId }`
   - Add a doc comment: "Our node is excluded by design — it is part of the user's equipment, not
     a mesh peer. Its position is displayed separately via the GPS location layer."
   - Update any usages of `isOurNode` in the mapping block (field no longer exists)

3. **`MainUiState.kt`** — add field:
   ```kotlin
   val connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected
   ```

4. **`MainViewModel.kt`** — inject `ObserveConnectionStatusUseCase`, subscribe in `init`:
   ```kotlin
   observeConnectionStatus(NoParams)
       .onEach { status -> _uiState.update { it.copy(connectionStatus = status) } }
       .launchIn(viewModelScope)
   ```

5. **`HudControlsLayer.kt`** — replace `IconButton(onMeshTestClick)` with `NodeStatusIndicator`:
   - Remove `onMeshTestClick` parameter
   - Add `connectionStatus: MeshConnectionStatus` and `nodesWithPositionCount: Int` parameters
   - Define `private const val RSSI_LOW_THRESHOLD = -90` at file level
   - Implement `NodeStatusIndicator` as a private composable in the same file:
     - Shows `--` (gray) when not `Connected`
     - Shows count (red if `rssi < RSSI_LOW_THRESHOLD`, green otherwise) when `Connected`
     - No click action

6. **`MainScreen.kt`** — pass `uiState.connectionStatus` and `uiState.nodeMarkers.size` to
   `HudControlsLayer`; remove `onMeshTestClick` parameter and its call sites.

7. **DI** — add `ObserveConnectionStatusUseCase` to `MainViewModel` factory/module.

**Skill**: Direct coding

**Output**: Buildable app, indicator visible on device, our node absent from map markers.

### Phase 2 — Simplify

Run `/simplify` on all changed files.

### Phase 3 — Architectural Review

**Goal**: Confirm no Clean Architecture violations.

**Tasks**: Review that:
- `connectionStatus` derivation stays in ViewModel, not in composable
- No domain types leak into HUD composable beyond what's mapped in ViewModel

**Skill**: `/architect review: app/src/main/java/.../feature/main/`

### Phase 4 — Skill Update Review

- **`/architect`**: Add the "our node is not a peer" rule to architect skill — any use case
  returning peer nodes must exclude our own node at the domain level.
- **`/ui-designer`**: New component `NodeStatusIndicator` — document color token usage (red/green
  for signal quality). If Design System has no signal-quality color tokens yet, this feature defines
  them; update skill.
- **`/icon-designer`**: No new icons. No changes needed.
- **`/planner`**: No methodology gaps found.

### Phase 5 — Project Docs & Memory Update

- Update `CLAUDE.md`:
  - Add feature row for this indicator → `Done`
  - Add a **Design Decisions** section (or append to existing) documenting: "Our node is excluded
    from peer node lists and map markers. It is part of user equipment. GPS position is shown via
    the location layer only."
- Set this plan's status to `Done`
- Follow-up task (not in this commit): add `[OWN NODE]` labeling in data-layer logs wherever our
  node data is processed

### Phase 6 — Commit

Stage files by name, commit with message:
```
feat(hud): индикатор подключения к ноде и числа нод с геопозицией
```

## Coordination Map

```
Phase 0: [Research — complete]
Phase 1: [Direct coding] — MainUiState → MainViewModel → HudControlsLayer → MainScreen → DI
Phase 2: /simplify on changed files
Phase 3: /architect review: feature/main/
Phase 4: [Skill update review — /ui-designer signal-quality color tokens]
Phase 5: [Docs & memory update]
Phase 6: /commit
```

## Open Questions

1. **Stale position filtering**: Currently no timestamp-based staleness check on geoposition data.
   Future feature — nodes that haven't reported position recently still count.
2. **`[OWN NODE]` log labeling**: not implemented in this commit. Tracked as follow-up — identify
   all data-layer log points where our node is processed and add explicit label.
3. **`Color.Black` hardcoded fallback в `HudInfoSlotItem.kt`**: в ветке ui_fixes_may изменён с
   `MaterialTheme.colorScheme.onSurface` на `Color.Black` — сломан HUD на тёмной теме.
   При реализации этого плана восстановить `onSurface` или заменить на правильный дизайн-токен.

## Отклонения от плана

`NodeStatusIndicator` как отдельный composable **не создавался**. Вместо этого:
- `infoBadge` на кнопке радио → счётчик нод с геопозицией
- `tintOverride` на кнопке радио → цвет по RSSI
- `RSSI_LOW_THRESHOLD` вынесен в `MainViewModel.kt`, не в `HudControlsLayer`

Архитектурно лучше плана — консистентно с HUD `HudButtonSlot`-паттерном.

## Change Log

- 2026-04-06: Created
- 2026-04-06: Clarified "our node" philosophy — excluded from map markers at domain level to avoid
  duplication with GPS location layer. Removed `isOurNode` from `NodeMarkerModel`. RSSI threshold
  confirmed at -90 dBm, extracted to named constant.
- 2026-05-25: Done. Реализовано через infoBadge+tintOverride на radio-кнопке HUD.
