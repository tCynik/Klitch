# Debug History: GPS Position Staleness
Ref: [.claude/docs/gps-position-staleness.md](.claude/docs/gps-position-staleness.md)

---

## 2026-06-06 | Locked phone position marker stays at old location and shows as "current"

**Symptom:** Phone B screen locked, moved to new location — marker on Phone A stays at old coordinates and shows "актуальная". After unlock, position lingers at old location, periodically oscillating between "актуальная" and "устаревшая".
**Tried:**
- Traced GPS timestamp pipeline through `GpsRepositoryImpl` → `MeshLocationRepositoryAdapter` → `AndroidMeshLocationManager` → `ObserveNodeMarkersUseCase` (`71a6863`) — `GpsLocation` had no `time` field; `location.time = 0` propagated through; `position.time = 0` in every outgoing packet; `ObserveNodeMarkersUseCase` fell back to `lastHeard` for stale detection; marker always "актуальная" while node in radio range; oscillation was radio signal fluctuation, not GPS freshness.
**Result:** fixed (`71a6863`) — Added `time: Long` to `GpsLocation`, propagated `androidLocation.time` through `GpsRepositoryImpl` and `MeshLocationRepositoryAdapter`; `position.time` now reflects actual GPS fix epoch; stale detection is honest.

---

## 2026-06-06 | Background GPS bridge stops on DeviceSleep (screen off)

**Symptom:** Same as above — marker stale while phone B screen off; periodic revivals on brief BLE reconnect.
**Tried:**
- Phase 1 background-position-pipeline (uncommitted) — `position.time=0` was necessary but not sufficient; root cause also `handleDeviceSleep()` calling `locationManager.stop()` and TX queue stopping while BLE sleeps; fix: keep GPS bridge alive during DeviceSleep, `flushLastPosition()` on reconnect, disable `is_power_saving` when enabling geo broadcast, propagate GPS fix time in `OnConnectPositionSender`.
**Result:** partially fixed — Phase 1 quick win implemented; field test pending (phone B screen off 10+ min → phone A `positionTime` < 2 min).

---
