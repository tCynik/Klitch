# Plan: App-Driven Smart Position Broadcasting

**Date**: 2026-06-07
**Status**: Done

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

### Phase 1 — Node config constant change + method rename ✅

### Phase 2 — Smart broadcaster in AndroidMeshLocationManager ✅

### Phase 3 — Freshness threshold alignment ✅

### Phase 4 — Documentation update ✅

### Phase 5 — Integration review ✅ (build verified; device logcat pending field test)

### Phase 6 — Skill update review ✅

### Phase 6b — Project docs & memory update ✅

### Phase 7 — Commit preparation (pending user confirmation)

---

## Open Questions (resolved in implementation)

1. **`lastSentAtMs` thread safety**: fields marked `@Volatile`; `flushLastPosition()` may race but worst case is one extra/missed gate cycle.
2. **Accuracy = 0**: `coerceAtLeast(1f)` accepted as-is.
3. **First send after start**: immediate on first fix (lastSentAtMs=0 bypasses mobile gate).
4. **Interval tuning**: starting values 30 s / 180 s; field-test pending.

---

## Change Log

- 2026-06-07: created
- 2026-06-07: implemented (Phases 1–6b)
