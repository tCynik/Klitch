# GPS Position Staleness — Pipeline & Stale Detection

## What it does

Ensures the position marker of a peer node on the map correctly reflects whether the GPS fix is fresh or stale. Fresh positions (< 2 min) are shown with normal colours; stale positions (2 min – 12 h) are shown as grey markers; positions older than 12 h are hidden.

## Key classes

- `GpsLocation` — domain GPS model; carries `time: Long` (Unix epoch ms of the fix); `domain/gps/model/`
- `GpsRepositoryImpl` — OS location listener; maps `androidLocation.time` → `GpsLocation.time`; `data/gps/`
- `MeshLocationRepositoryAdapter` — bridges `GpsLocation` to mesh `Location`; sets `location.time = gpsLocation.time`; `data/gps/`
- `AndroidMeshLocationManager` — builds `ProtoPosition`; uses `location.time` for `position.time`; `mesh/service/`
- `ObserveNodeMarkersUseCase` — stale detection: `effectiveTime = if (positionTime > 0) positionTime else lastHeard`; `domain/map/usecase/`
- `OnConnectPositionSender` — sends own position on connect; requests positions from nodes with `receivedOnSlot == null`; `data/mesh/`

## Non-obvious decisions

- **`position.time = 0` was the silent staleness killer**: `GpsLocation` originally had no `time` field → `location.time = 0` propagated through → `position.time = 0` in every outgoing packet → `ObserveNodeMarkersUseCase` always fell back to `lastHeard` for stale detection → marker showed "актуальная" as long as the node was in radio range, regardless of GPS fix age.
- **Stale thresholds**: `POSITION_FRESHNESS_SECONDS = 120` (2 min) — fresh vs grey; `MAX_POSITION_AGE_SECONDS = 43200` (12 h) — hidden vs shown.
- **`lastHeard` fallback is intentional**: if `positionTime = 0` (node never sent GPS), `lastHeard` is used as a best-effort estimate. This means a node with no position data appears grey ("stale-online") when active, not hidden.
- **Background GPS limitation**: when Phone B screen is locked, Android may throttle GPS updates (manufacturer-dependent). The stale indicator correctly reflects this: if no fresh fix arrives, the marker turns grey. This is OS-level behaviour, not fixable in app code.
- **`OnConnectPositionSender` only requests positions for `receivedOnSlot == null` nodes**: nodes with a known slot from a previous session are not re-requested on connect — they appear on the map when they send their next scheduled broadcast.

## Timestamp pipeline

```
androidLocation.time (epoch ms)
  → GpsLocation.time
    → Location("gps-service").time
      → ProtoPosition.time (epoch seconds)
        → Node.position.time
          → MeshNodeModel.positionTime
            → ObserveNodeMarkersUseCase.effectiveTime
              → NodeMarkerModel.isStale
```

## Source

Debug: `.claude/debug/gps-position-staleness.md`
