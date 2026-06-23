# GPS Position Staleness — Pipeline, Stale Detection & App-Driven Broadcasting

## What it does

Ensures the position marker of a peer node on the map correctly reflects whether the GPS fix is
fresh or stale. Fresh positions (< 9 min) are shown with normal colours; stale positions
(9 min – 12 h) are shown as grey markers; positions older than 12 h are hidden.

**The app is responsible for sending its own position into the mesh** (not the firmware). The
connected node is silenced (`position_broadcast_secs = Int.MAX_VALUE`). When the operator
disconnects from the radio, position packets stop, `positionTime` ages on peer devices, and the
marker transitions to stale automatically.

## Key classes

- `GpsLocation` — domain GPS model; carries `time: Long` (Unix epoch ms of the fix); `domain/gps/model/`
- `GpsRepositoryImpl` — OS location listener; maps `androidLocation.time` → `GpsLocation.time`; `data/gps/`
- `MeshLocationRepositoryAdapter` — bridges `GpsLocation` to mesh `Location`; sets `location.time = gpsLocation.time`; `data/gps/`
- `AndroidMeshLocationManager` — builds `ProtoPosition`; applies smart interval filter before sending; **sole position broadcaster** into the mesh; `mesh/service/`
- `ObserveNodeMarkersUseCase` — stale detection via `positionTime` only (no `lastHeard` fallback); `domain/map/usecase/`
- `OnConnectPositionSender` — sends own position on connect to all active contour slots; `data/mesh/`
- `SyncContoursOnConnectUseCase` — writes `position_broadcast_secs = Int.MAX_VALUE` to the node on connect, silencing autonomous firmware broadcasts; `domain/channel/usecase/`

## Why the app broadcasts instead of the firmware

Meshtastic firmware stamps `current_time` on every autonomous position re-broadcast. This makes
peer markers appear perpetually fresh — `positionTime` never ages — even after the operator
physically disconnects their phone from the radio. There is no "BLE client disconnected" signal
in the Meshtastic mesh protocol.

**Fix**: disable the node's autonomous broadcast (`position_broadcast_secs = Int.MAX_VALUE`); the
app sends position on its own schedule. When the phone disconnects, position packets stop, and
peer markers correctly transition to stale after `POSITION_FRESHNESS_SECONDS`.

## App-side smart broadcast algorithm

`AndroidMeshLocationManager` receives every GPS fix from `locationRepository.getLocations()` and
decides whether to send using a distance + time dual-gate:

```
constants:
  MOBILE_INTERVAL_MS   = 30 000   // minimum gate — no send faster than this
  STATIONARY_INTERVAL_MS = 180 000 // maximum gap — heartbeat when stationary

state:
  lastSentAtMs  — epoch ms of last send (0 = never)
  lastSentLat/Lon — coordinates of last send (NaN = never)

on each GPS fix:
  elapsed = now - lastSentAtMs
  if elapsed < MOBILE_INTERVAL_MS → skip (gate)

  distanceM = distanceBetween(lastSent, current)   // MAX_VALUE on first fix
  accuracyM = location.accuracy.coerceAtLeast(1f)
  hasMoved  = distanceM > accuracyM                 // noise filter (accuracy IS the buffer)

  send if (hasMoved || elapsed >= STATIONARY_INTERVAL_MS)
```

Reason is logged under tag `MT/SmartPos`: `"send distance"`, `"send heartbeat"`, `"skip gate"`,
`"skip noise"`.

## Threshold relationship

All cadence constants derive from a single canonical source —
`PositionTrackingPolicy` (`mesh/service/PositionTrackingPolicy.kt`, see
`docs/plans/position-broadcast-interval-unification.md`):

```kotlin
object PositionTrackingPolicy {
    const val STATIONARY_INTERVAL_SECS = 180
    const val MOBILE_MIN_GATE_SECS = 30
    const val STALENESS_MULTIPLIER = 3
}
```

`POSITION_FRESHNESS_SECONDS = STATIONARY_INTERVAL_SECS * STALENESS_MULTIPLIER`. A node that
missed `STALENESS_MULTIPLIER` consecutive expected broadcasts is considered stale. The same
`STATIONARY_INTERVAL_SECS` also drives the NODE_GPS firmware preset written by
`BackgroundPositionSession` (`docs/plans/node-gps-position-source.md`) — PHONE_GPS heartbeat,
NODE_GPS firmware broadcast, and map staleness all share one cadence.

| Constant | Value | Location |
|---|---|---|
| `STATIONARY_INTERVAL_MS` | 180 000 ms (180 s), `= PositionTrackingPolicy.STATIONARY_INTERVAL_SECS * 1000L` | `AndroidMeshLocationManager` |
| `POSITION_FRESHNESS_SECONDS` | 540 s (3 × 180), derived | `ObserveNodeMarkersUseCase` |
| `MAX_POSITION_AGE_SECONDS` | 43 200 s (12 h), independent invariant — not part of cadence-derivation | `ObserveNodeMarkersUseCase` |

When tuning cadence, change only `PositionTrackingPolicy.STATIONARY_INTERVAL_SECS` — every
derived threshold follows automatically.

## Stale detection logic

```kotlin
// ObserveNodeMarkersUseCase
val isStale = node.positionTime <= (nowSeconds - POSITION_FRESHNESS_SECONDS)
```

- `positionTime` is the **only** source for staleness — no `lastHeard` fallback
- `positionTime == 0` → node never sent an accepted position → hidden (filtered by `maxAgeThreshold`)
- Stale status is re-evaluated every 10 s via `staleTicker()` so markers transition dynamically

## Non-obvious decisions

- **No `lastHeard` fallback**: previously `effectiveTime = if (positionTime > 0) positionTime else lastHeard`.
  Removed because staleness must reflect position freshness specifically — a node active on the network
  but not sending GPS should not appear position-fresh. `positionTime == 0` → hidden, not grey.
- **`position.time = 0` was the silent staleness killer** (historical): `GpsLocation` originally
  had no `time` field → `location.time = 0` propagated → `position.time = 0` in every outgoing
  packet → marker showed fresh forever. Fixed by adding `time: Long` to `GpsLocation`.
- **`flushLastPosition()` on BLE reconnect**: `AndroidMeshLocationManager` caches the last built
  `ProtoPosition` and re-sends it immediately on reconnect to close short BLE gap windows.
- **`is_power_saving = false`** is written to the node on connect (inside
  `prepareNodeForAppDrivenBroadcast()`) to keep BLE alive in the background.

## Known limitations / planned extensions

- **Unattended beacon use case broken**: leaving a powered radio without a connected phone — marker
  goes stale after 9 min once the phone disconnects. A dedicated "beacon node" configuration is a
  planned separate task.
- **SOS mode**: SOS position handling is currently app-driven (same architecture). Full SOS rework
  (aggressive interval, message-with-coordinates on entry) is a deferred task.

## Timestamp pipeline

```
androidLocation.time (epoch ms)
  → GpsLocation.time
    → Location("gps-service").time
      → ProtoPosition.time (epoch seconds)  ← set by AndroidMeshLocationManager
        → Node.position.time
          → MeshNodeModel.positionTime
            → ObserveNodeMarkersUseCase: positionTime → isStale
              → NodeMarkerModel.isStale
```

## Source

Debug: `docs/debug/gps-position-staleness.md`
Plan: `docs/archive/app-driven-position-broadcasting.md`
