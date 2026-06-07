# Plan: App-Driven Smart Position Broadcasting

**Date**: 2026-06-07
**Status**: Approved

---

## Summary

Meshtastic firmware stamps `current_time` on every autonomous position re-broadcast, making peer
markers appear perpetually fresh even after the operator has disconnected their phone from the
radio. Because the mesh protocol carries no "BLE client disconnected" signal, the only fix is to
transfer broadcast responsibility from the firmware to the app: the node is configured with
`position_broadcast_secs = Int.MAX_VALUE` (silent), and `AndroidMeshLocationManager` takes over
the periodic send using a distance-based smart algorithm. When the phone disconnects from BLE the
node goes quiet, `positionTime` on peer devices ages naturally, and markers correctly transition
to stale (grey) after the freshness threshold.

This is an architectural shift: **the node is no longer the position source in the mesh — the app
is.** The "unattended beacon" use case (radio left on without a connected phone) is intentionally
broken by this change and will be handled via a separate task with a different configuration
approach.

---

## Scope

**In scope**
- Disable node's autonomous position broadcast in normal GPS-on mode (`position_broadcast_secs = Int.MAX_VALUE`)
- App-side smart position broadcaster with distance + accuracy noise filter and dual-interval logic
- Shared constant tying `STATIONARY_BROADCAST_INTERVAL_S` to `POSITION_FRESHNESS_SECONDS`
- Rename `enableNodePositionBroadcastReady()` → `prepareNodeForAppDrivenBroadcast()` to reflect new semantics
- Documentation: gps-position-staleness.md, location-sharing-config.md, debug log entry

**Out of scope**
- SOS mode position behavior (unchanged: node stays MAX_VALUE, app drives; full SOS rework is a separate task)
- "Unattended beacon" node type (separate task)
- UI/settings changes (no new user-facing controls)
- `flushLastPosition()` on reconnect (unchanged; closes short BLE gaps)

---

## Architecture Notes

### Constant relationship

`POSITION_FRESHNESS_SECONDS` (staleness threshold in `ObserveNodeMarkersUseCase`) must always be
strictly greater than `STATIONARY_BROADCAST_INTERVAL_S` (max send interval in
`AndroidMeshLocationManager`) to avoid marker flicker. Required buffer ≥ 60 s.

Starting values:
- `STATIONARY_BROADCAST_INTERVAL_S = 180` → node appears stale 180 s after last app send
- `MOBILE_BROADCAST_INTERVAL_S = 30` → minimum time gate before any distance-triggered send
- `POSITION_FRESHNESS_SECONDS = 300` (unchanged) → 120 s buffer, no flicker

Both sides document the relationship in a comment; no shared constant object is introduced (avoids
cross-module dependency between `mesh` and `domain` layers).

### Node config change path

`SyncContoursOnConnectUseCase` is the single write point for node position config on connect.
The flow stays identical structurally; only the constant changes:

| State | Old `position_broadcast_secs` | New |
|-------|-------------------------------|-----|
| GPS broadcast enabled, not SOS | 60 | `Int.MAX_VALUE` |
| GPS broadcast disabled | `Int.MAX_VALUE` | `Int.MAX_VALUE` (unchanged) |
| SOS active | `Int.MAX_VALUE` | `Int.MAX_VALUE` (unchanged) |

`prepareNodeForAppDrivenBroadcast()` replaces `enableNodePositionBroadcastReady()`. It writes
`position_broadcast_secs = Int.MAX_VALUE`, `position_broadcast_smart_enabled = false`, and
disables `is_power_saving` — same as before except for the secs value.

### Smart broadcast algorithm

Runs inside `AndroidMeshLocationManager.start()` on each incoming `Location` from
`locationRepository.getLocations()`:

```
state: lastSentAtMs = 0L, lastSentLat = NaN, lastSentLon = NaN

onEach location:
    val elapsedMs = now - lastSentAtMs

    // 1. Mobile interval gate — hard minimum before any send
    if (elapsedMs < MOBILE_INTERVAL_MS) → skip (log: "gate")

    // 2. Distance check with accuracy noise filter
    val distanceM = distanceBetween(lastSentLat/Lon, location.lat/lon)   // MAX_VALUE if first fix
    val accuracyM = location.accuracy.coerceAtLeast(1f)
    val hasMoved  = distanceM > accuracyM

    // 3. Decision
    val stationaryExpired = elapsedMs >= STATIONARY_INTERVAL_MS
    if (hasMoved || stationaryExpired):
        send(pos)
        lastSentAtMs = now
        lastSentLat  = location.latitude
        lastSentLon  = location.longitude
        log reason: "distance" | "heartbeat"
    else:
        log: "skip dist=Xm acc=Ym elapsed=Zs"
```

`flushLastPosition()` on BLE reconnect: sends cached `lastPosition` proto and updates
`lastSentAtMs = now` to reset the interval.

`stop()`: clears `lastSentAtMs`, `lastSentLat`, `lastSentLon`.

`distanceBetween()`: use `android.location.Location.distanceBetween(lat1, lon1, lat2, lon2,
results)` — returns distance in metres in `results[0]`.

### Logging

Tag: `MT/SmartPos`
Level: `Logger.d` for skip/send decisions, `Logger.i` for first send after start/reconnect.

Format examples:
```
MT/SmartPos  send distance: dist=47.3m acc=12.0m elapsed=31s
MT/SmartPos  send heartbeat: dist=2.1m acc=8.0m elapsed=181s
MT/SmartPos  skip gate: elapsed=4s < 30s
MT/SmartPos  skip noise: dist=6.3m <= acc=9.5m elapsed=35s
```

---

## Phase Plan

### Phase 1 — Node config constant change + method rename

**Goal**: Node always receives `position_broadcast_secs = Int.MAX_VALUE` in normal GPS-on mode.
Semantics are clearly expressed in method and constant names.

**Tasks**:
1. `MeshConfigRepositoryImpl.kt`
   - Rename `GEO_BROADCAST_READY_SECS = 60` → keep name, change value to `Int.MAX_VALUE`
   - Rename `enableNodePositionBroadcastReady()` → `prepareNodeForAppDrivenBroadcast()`
   - Update the early-return guard inside: check `position_broadcast_secs == Int.MAX_VALUE &&
     !smart_enabled && !power_saving` instead of `== 60`
2. `MeshConfigRepository.kt` (interface)
   - Rename `enableNodePositionBroadcastReady()` → `prepareNodeForAppDrivenBroadcast()`; update
     KDoc to describe new semantics
3. `SyncContoursOnConnectUseCase.kt`
   - Rename call site: `enableNodePositionBroadcastReady()` → `prepareNodeForAppDrivenBroadcast()`
   - `BROADCAST_READY_SECS = 60` → `BROADCAST_READY_SECS = Int.MAX_VALUE`
   - Add comment explaining that both READY and DISABLED now write the same value; the distinction
     is behavioral (`is_power_saving` fix) and semantic
4. `EnableNodePositionBroadcastReadyUseCase.kt` — rename file and class →
   `PrepareNodeForAppDrivenBroadcastUseCase.kt`, update DI binding in `MeshDataModule.kt` /
   `UserSettingsModule.kt`
5. `IsPositionSmartBroadcastEnabledUseCase.kt` — verify it is still needed after the change; if
   the check `currentSmartEnabled == true` still guards `needsBroadcastWrite`, keep as-is

**Skill**: direct coding
**Output**: compiling code; on-connect the node receives `position_broadcast_secs = Int.MAX_VALUE`

---

### Phase 2 — Smart broadcaster in AndroidMeshLocationManager

**Goal**: Position is sent only when the operator has moved beyond GPS noise, or when the
stationary heartbeat interval fires. Sends stop when BLE disconnects.

**Tasks**:
1. `AndroidMeshLocationManager.kt`
   - Add companion constants:
     ```kotlin
     private const val MOBILE_INTERVAL_MS   = 30_000L   // min gate before any distance-send
     private const val STATIONARY_INTERVAL_MS = 180_000L // max gap — heartbeat
     // POSITION_FRESHNESS_SECONDS in ObserveNodeMarkersUseCase (300 s) must stay >
     // STATIONARY_INTERVAL_MS / 1000 (180 s). Buffer = 120 s. Adjust both together.
     ```
   - Add mutable state fields (not thread-safe — all access is on `IO` dispatcher, single flow):
     `var lastSentAtMs = 0L`, `var lastSentLat = Double.NaN`, `var lastSentLon = Double.NaN`
   - Replace the direct `sendPositionFn(pos)` call with `smartSend(location, pos, sendPositionFn)`
   - Implement `smartSend()` per the algorithm in Architecture Notes
   - `stop()`: add `lastSentAtMs = 0L; lastSentLat = Double.NaN; lastSentLon = Double.NaN`
   - `flushLastPosition()`: after `sendPositionFn?.invoke(pos)` add `lastSentAtMs =
     System.currentTimeMillis()`
2. Add `distanceBetween()` private helper (thin wrapper around
   `android.location.Location.distanceBetween`)

**Skill**: direct coding
**Output**: smart interval active; logs visible in logcat under `MT/SmartPos`

---

### Phase 3 — Freshness threshold alignment

**Goal**: Relationship between `POSITION_FRESHNESS_SECONDS` and `STATIONARY_INTERVAL_MS` is
explicitly documented so future tuning of one value prompts updating the other.

**Tasks**:
1. `ObserveNodeMarkersUseCase.kt`
   - Update comment above `POSITION_FRESHNESS_SECONDS`:
     ```kotlin
     // Threshold for fresh vs stale. Must exceed AndroidMeshLocationManager.STATIONARY_INTERVAL_MS / 1000
     // (currently 180 s) with a buffer of at least 60 s. Current buffer: 300 - 180 = 120 s.
     // When tuning STATIONARY_INTERVAL_MS, adjust this value accordingly.
     private const val POSITION_FRESHNESS_SECONDS = 5 * 60
     ```
   - Value stays 300 s — no numeric change

**Skill**: direct coding
**Output**: documentation-only change; no behavioral delta

---

### Phase 4 — Documentation update

**Goal**: Architecture docs reflect the new model; future sessions start with correct context.

**Tasks**:
1. `.claude/docs/gps-position-staleness.md`
   - Add "App-Driven Broadcasting" section describing the architectural shift and why
   - Update "Key classes" to include `AndroidMeshLocationManager` smart-send role
   - Update "Non-obvious decisions" with the firmware `current_time` problem and the fix
2. `.claude/docs/location-sharing-config.md`
   - Update to note `position_broadcast_secs = Int.MAX_VALUE` is now the normal state when GPS
     is enabled; node never broadcasts autonomously in regular mode
3. `.claude/debug/gps-position-staleness.md`
   - Add entry marking the root cause confirmed and resolution implemented

**Skill**: direct editing
**Output**: updated docs

---

### Phase 5 — Integration review

**Goal**: Confirm no regressions in SOS path, reconnect flush, and power-saving fix.

**Tasks**:
1. Trace `SyncContoursOnConnectUseCase` for SOS=true path:
   - `desiredBroadcastEnabled = false` → `disableNodePositionBroadcast()` called → MAX_VALUE written
   - SOS path is unchanged ✓
2. Trace reconnect flow in `AndroidMeshLocationManager`:
   - `flushLastPosition()` called → last proto sent → `lastSentAtMs` updated → correct ✓
3. Verify `is_power_saving = false` write is still inside `prepareNodeForAppDrivenBroadcast()` ✓
4. Build and run on device; verify `MT/SmartPos` logs appear and cadence matches algorithm

**Skill**: direct coding + device verification
**Output**: no regressions; smart-send cadence confirmed in logcat

---

### Phase 6 — Skill update review

**Tasks** (check each skill — update or explicitly state "no change needed"):

- `/architect` — new pattern: position broadcast responsibility shifted to app layer; node config
  `position_broadcast_secs` is now always `Int.MAX_VALUE` for app-managed nodes. Document in
  architect skill under "Position Broadcasting".
- `/ui-designer` — no UI changes → **no update needed**
- `/icon-designer` — no new icons → **no update needed**
- `/tester` — no new test patterns introduced → **no update needed**
- `/planner` — no planning methodology gaps found → **no update needed**

**Skill**: direct edit of `.claude/commands/architect.md` if update needed
**Output**: skill files accurate or explicitly confirmed unchanged

---

### Phase 6b — Project docs & memory update

**Tasks**:
1. `CLAUDE.md` — no feature row change (this is an internal architectural fix to existing GPS
   pipeline, not a new feature in the status table)
2. Create/update `.claude/docs/gps-position-staleness.md` (Phase 4 handles this)
3. Move this plan to `.claude/archive/app-driven-position-broadcasting.md`; delete from `plans/`
4. Memory: update `project_state.md` if relevant; add note to `workflow_feedback.md` about the
   firmware timestamp trap (pattern worth remembering for future Meshtastic integrations)
5. Record token cost in Change Log below

**Skill**: direct edit
**Output**: CLAUDE.md accurate, plan archived, memory updated

---

### Phase 7 — Commit preparation

Standard: `git status` → stage by file name → propose commit message in Russian → wait for
confirmation → `git commit`.

Suggested commit message scope: `gps: переход на отправку позиции из приложения (smart interval)`

---

## Coordination Map

```
Phase 0:  SKIP — domain fully understood
Phase 1:  [direct coding] — node config + method rename
Phase 2:  [direct coding] — AndroidMeshLocationManager smart algorithm
Phase 3:  [direct coding] — freshness threshold comment
Phase 4:  [direct editing] — docs update
Phase 5:  [direct coding + device run] — integration review
Phase 6:  [architect.md edit if needed] — skill update review
Phase 6b: [direct editing] — CLAUDE.md, archive plan, memory
Phase 7:  [git] — stage → propose → confirm → commit
```

---

## Open Questions

1. **`lastSentAtMs` thread safety**: `AndroidMeshLocationManager.start()` collects on the `IO`
   dispatcher via `launchIn(scope)`. State fields are written only from that single coroutine;
   `flushLastPosition()` may be called from another coroutine. If this races, wrap state in a
   `Mutex` or make fields `@Volatile`. Assess during Phase 2 implementation.

2. **Accuracy = 0**: Some devices report `location.accuracy = 0.0` when accuracy is unavailable.
   The `coerceAtLeast(1f)` guard prevents division-by-zero but means any non-zero distance triggers
   a send. Verify this is acceptable or add a minimum accuracy requirement (e.g., ignore fixes with
   accuracy > 100 m).

3. **First send after start**: `lastSentLat = NaN` on first fix → `distanceBetween` not called →
   distance defaults to `MAX_VALUE` → send immediately after `MOBILE_INTERVAL_MS` (30 s). This
   gives a fast first-send on connect. Confirm this is the desired UX.

4. **Interval tuning**: `STATIONARY_INTERVAL_MS = 180 s` and `MOBILE_INTERVAL_MS = 30 s` are
   starting values. Both are `private const` in `AndroidMeshLocationManager`; changing them
   requires a rebuild. Field-test and adjust.

---

## Change Log

- 2026-06-07: created
- tokens: not recorded
