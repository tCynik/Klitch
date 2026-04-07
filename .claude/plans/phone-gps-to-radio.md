# Plan: Phone GPS to Radio Node

**Date**: 2026-04-06
**Status**: Approved

## Summary

MeshTactics does not send the phone's GPS coordinates to the connected Meshtastic radio node. As a result, the node broadcasts stale cached coordinates from a previous session with the official Meshtastic app. Other nodes see incorrect map markers.

The complete sending pipeline already exists in the mesh module: `AndroidMeshLocationManager` collects phone GPS from `LocationRepository` and calls `CommandSender.sendPosition()` on each update. The pipeline is gated by `UiPrefs.shouldProvideNodeLocation(nodeNum)`, which defaults to `false` and has no UI toggle in MeshTactics. Enabling the feature requires: (1) changing the default to `true`, and (2) adding a user-facing toggle in NodeSettings.

## Research Findings (Phase 0 — complete)

- **Sending infrastructure**: `AndroidMeshLocationManager.start(scope, sendPositionFn)` → `CommandSender.sendPosition(ProtoPosition)` → `PacketHandlerImpl.sendToRadio` → BLE `toRadio` characteristic. Fully implemented.
- **Gate**: `MeshConnectionManagerImpl:114` reads `UiPrefs.shouldProvideNodeLocation(myNodeNum)`. If `false`, `locationManager.start()` is never called.
- **Default**: `UiPrefsImpl:140` — `it[key] ?: false` — off by default.
- **Setter**: `UiPrefs.setShouldProvideNodeLocation(nodeNum, Boolean)` exists and writes to DataStore.
- **Location source**: `LocationRepositoryImpl` — separate from `AppLocationProvider`, uses Android `LocationManager` directly. Independent from the map display layer.
- **Interval**: position is sent on every GPS update from `LocationRepository` (no throttling in `AndroidMeshLocationManager`). Acceptable for MVP.

## Scope

**In scope:**
- Change default to `true` so the radio gets GPS immediately after connection (Phase 1)
- User toggle "Send location to node" in NodeSettings (Phase 2)

**Out of scope:**
- Custom update interval (leave to mesh module defaults)
- Location accuracy / battery optimization
- Sending location to non-connected peer nodes

## Architecture Notes

- No domain layer changes — `UiPrefs` interface already has `shouldProvideNodeLocation` / `setShouldProvideNodeLocation`.
- `MeshConnectionManagerImpl` uses `UiPrefs` (not `MeshPrefs`) — do not confuse the two implementations.
- `NodeSettingsViewModel` must access `UiPrefs` via DI. Verify injection path before Phase 2.
- Phase 1 is a single-line change; Phase 2 is a standard ViewModel + Screen pattern.

## Phase Plan

### Phase 0 — Research ✅ Done
- **Output**: findings documented in this plan (see Research Findings above)

### Phase 1 — Quick Fix (default to `true`)
- **Goal**: radio receives phone GPS immediately after connection, no UI required
- **Tasks**:
  - `UiPrefsImpl.kt:140` — change `?: false` to `?: true`
- **Skill**: direct coding
- **Output**: single-file change; radio starts receiving GPS on next connection

### Phase 2 — UI Toggle in NodeSettings
- **Goal**: user can disable location sharing per node
- **Tasks**:
  1. Verify `UiPrefs` DI injection path into `NodeSettingsViewModel`
  2. Add `provideLocation: Boolean` to `NodeSettingsUiState`
  3. Add `fun onProvideLocationToggled(enabled: Boolean)` in `NodeSettingsViewModel` — calls `uiPrefs.setShouldProvideNodeLocation(nodeNum, enabled)`
  4. Add `SwitchPreference` item "Send location to node" in `NodeSettingsScreen`
- **Skill**: direct coding
- **Output**: working toggle, persisted in DataStore

### Phase 3 — Integration Review
- **Goal**: confirm no Clean Architecture violations
- **Tasks**: review that `UiPrefs` is not called outside ViewModels in presentation layer
- **Skill**: `/architect review: UiPrefsImpl, NodeSettingsViewModel`
- **Output**: review report, violations fixed

### Phase 4 — Skill & Docs Update
- **Goal**: project metadata reflects completed feature
- **Tasks**:
  - Update `CLAUDE.md` feature table
  - Set this plan status to `Done`
  - Update `memory/project_state.md`
- **Skill**: direct edit
- **Output**: accurate documentation

### Phase 5 — Commit
- **Tasks**: stage `UiPrefsImpl.kt` + `NodeSettings*` files, commit via `/commit`
- **Skill**: `/commit`
- **Output**: clean `git status`

## Coordination Map

```
Phase 0: [Research — done]
Phase 1: [direct coding — UiPrefsImpl.kt, 1 line]
Phase 2: [direct coding — NodeSettingsUiState, NodeSettingsViewModel, NodeSettingsScreen]
Phase 3: /architect review: UiPrefsImpl, NodeSettingsViewModel
Phase 4: [docs update — CLAUDE.md, plan file, memory/]
Phase 5: /commit
```

## Open Questions

1. **Phase 1 vs Phase 2 priority**: Is the default-`true` fix sufficient for current testing, or is the UI toggle needed now?
2. **NodeSettingsViewModel DI**: Does `NodeSettingsViewModel` currently have `UiPrefs` injected, or does it need to be added to the constructor?
3. **Throttling**: `AndroidMeshLocationManager` sends on every GPS update. If `LocationRepository` emits frequently, this may spam the BLE channel. Monitor in field testing.

## Change Log

- 2026-04-06: created
