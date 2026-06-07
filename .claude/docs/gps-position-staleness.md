# GPS Position Staleness — Pipeline, Stale Detection & App-Driven Broadcasting

## What it does

Ensures the position marker of a peer node on the map correctly reflects whether the GPS fix is
fresh or stale. Fresh positions (< 5 min) are shown with normal colours; stale positions
(5 min – 12 h) are shown as grey markers; positions older than 12 h are hidden.

**The app is responsible for sending its own position into the mesh** (not the firmware). The
connected node is silenced (`position_broadcast_secs = Int.MAX_VALUE`). When the operator
disconnects from the radio, position packets stop, `positionTime` ages on peer devices, and the
marker transitions to stale automatically.

## Key classes

- `GpsLocation` — domain GPS model; carries `time: Long` (Unix epoch ms of the fix); `domain/gps/model/`
- `GpsRepositoryImpl` — OS location listener; maps `androidLocation.time` → `GpsLocation.time`; `data/gps/`
- `MeshLocationRepositoryAdapter` — bridges `GpsLocation` to mesh `Location`; sets `location.time = gpsLocation.time`; `data/gps/`
- `AndroidMeshLocationManager` — builds `ProtoPosition`; applies smart interval filter before sending; **sole position broadcaster** into the mesh; `mesh/service/`
- `ObserveNodeMarkersUseCase` — stale detection: `effectiveTime = if (positionTime > 0) positionTime else lastHeard`; `domain/map/usecase/`
- `OnConnectPositionSender` — sends own position on connect; requests positions from nodes with `receivedOnSlot == null`; `data/mesh/`
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
  hasMoved  = distanceM > accuracyM                 // noise filter

  send if (hasMoved || elapsed >= STATIONARY_INTERVAL_MS)
```

Reason is logged under tag `MT/SmartPos`: `"send distance"`, `"send heartbeat"`, `"skip gate"`,
`"skip noise"`.

## Threshold relationship (must stay in sync)

`POSITION_FRESHNESS_SECONDS` in `ObserveNodeMarkersUseCase` must always exceed
`STATIONARY_INTERVAL_MS / 1000` in `AndroidMeshLocationManager` with a buffer of at least 60 s
to prevent marker flicker:

| Constant | Value | Location |
|---|---|---|
| `STATIONARY_INTERVAL_MS` | 180 000 ms (180 s) | `AndroidMeshLocationManager` |
| `POSITION_FRESHNESS_SECONDS` | 300 s | `ObserveNodeMarkersUseCase` |
| Buffer | 120 s | — |

When tuning one, update the other. Both files carry a cross-reference comment.

## Stale detection logic

```kotlin
// ObserveNodeMarkersUseCase
val effectiveTime = if (node.positionTime > 0) node.positionTime else node.lastHeard
val isStale = effectiveTime <= (nowSeconds - POSITION_FRESHNESS_SECONDS)
```

- `POSITION_FRESHNESS_SECONDS = 300` (5 min) — fresh vs grey
- `MAX_POSITION_AGE_SECONDS = 43 200` (12 h) — shown vs hidden

Stale status is re-evaluated every 10 s via `staleTicker()` so markers transition dynamically.

## Non-obvious decisions

- **`position.time = 0` was the silent staleness killer** (historical): `GpsLocation` originally
  had no `time` field → `location.time = 0` propagated through → `position.time = 0` in every
  outgoing packet → `ObserveNodeMarkersUseCase` always fell back to `lastHeard` → marker showed
  "актуальная" as long as the node was in range. Fixed by adding `time: Long` to `GpsLocation`.
- **`lastHeard` fallback is intentional**: if `positionTime = 0` (node never sent GPS, e.g. older
  firmware), `lastHeard` is used as a best-effort estimate. Such a node appears grey
  ("stale-online") when active, not hidden.
- **`OnConnectPositionSender` only requests positions for `receivedOnSlot == null` nodes**: nodes
  with a known slot are not re-requested on connect — they appear when they send their next packet.
- **`flushLastPosition()` on BLE reconnect**: `AndroidMeshLocationManager` caches the last built
  `ProtoPosition` and re-sends it immediately on reconnect to close short BLE gap windows.
- **`is_power_saving = false`** is written to the node on connect (inside
  `prepareNodeForAppDrivenBroadcast()`) to keep BLE alive in the background so the app can send
  positions while the phone screen is off.

## Known limitations / planned extensions

- **Unattended beacon use case broken**: leaving a powered radio without a connected phone used to
  keep the marker visible on peers (firmware broadcasting). With app-driven broadcasting, the marker
  goes stale after 5 min once the phone disconnects. A dedicated "beacon node" configuration is a
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
            → ObserveNodeMarkersUseCase.effectiveTime
              → NodeMarkerModel.isStale
```

## Source

Debug: `.claude/debug/gps-position-staleness.md`
Plan: `.claude/archive/app-driven-position-broadcasting.md`
