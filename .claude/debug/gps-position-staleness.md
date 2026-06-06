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

## 2026-06-06 | Phase 1 committed — GPS bridge survives DeviceSleep

**Symptom:** Follow-up to entry above; Phase 1 fix was implemented but not yet committed.
**Tried:**
- Committed Phase 1 pipeline fix (`9a705e2`): `locationManager.stop()` removed from `handleDeviceSleep()`; `flushLastPosition()` called on `DeviceSleep → Connected` transition; `AndroidMeshLocationManager` stores `lastPosition` and `sendPositionFn`; `is_power_saving=false` written when enabling geo broadcast; `OnConnectPositionSender` uses GPS fix time with `nowMillis` fallback.
**Result:** fixed (`9a705e2`) — GPS→radio bridge now stays alive during screen-off BLE sleep; reconnect flushes a fresh position immediately; node `is_power_saving` disabled to keep BLE stable in background.

---

## 2026-06-06 | Second node with slot=null takes a long time to appear on map after app restart

**Symptom:** After app restart, second node visible in logs with `receivedOnSlot=null` for an extended period; node absent from map until slot resolved. `passesContourFilter` returns `false` for `null`, so node is hidden until `positionChannel` set via a live position packet.
**Tried:**
- Identified that `requestPositionsForUnknownSlots` is called at the end of `sendPosition()`, which first blocks on `gpsRepository.location.filterNotNull().first()` — if GPS cold-starts after restart, the request is delayed by the full GPS acquisition time. Decoupled: launch `requestPositionsForUnknownSlots` as a separate coroutine immediately at `Connected`, passing cached GPS or zero position; `want_response=true` triggers the remote node's reply regardless of payload (`afabe51`).
- Added spam filter: was sending position requests to all nodes with `slot=null` including stale/offline ones; filtered to nodes with `lastHeard >= now - 30 min` (`afabe51`).
- Added diagnostics: MT/GPS logs in `OnConnectPositionSender` (GPS cache state, request count, per-node request); `NodeMgr` tag in `NodeManagerImpl.handleReceivedPosition` (incoming packet + positionChannel transition); MT/PhoneGPS→radio tag with lat/lon in `flushLastPosition` (`afabe51`).
**Result:** unresolved — problem stopped reproducing after logging was added; root cause not confirmed. Monitoring continues with logging in place.

---

## 2026-06-06 | Peer node periodically turns grey (isStale=true) at 2.5m separation, phones stationary

**Symptom:** Two phones with nodes 2.5 m apart; both screens on; node marker on each phone turns grey periodically. Staleness confirmed (not disappearance). Reproduced after GPS timestamp fix was deployed.
**Tried:**
- Radio proximity interference hypothesis — ruled out by moving radios to 2.5 m; grey markers persisted.
- GPS fix timestamp staleness in `AndroidMeshLocationManager` — `location.time` from Android GPS not refreshed when device stationary; app was sending old GPS epoch in `position.time`; receiver marked node stale. Fix: if `nowMs - location.time > 90 000 ms`, use `nowMs` as `position.time` (no commit yet at time of writing). Issue persisted after fix → not the primary cause.
- `SyncContoursOnConnectUseCase.needsBroadcastWrite` checked only `position_broadcast_secs` (not `position_broadcast_smart_enabled`). If the radio had `secs=60` from a prior session but `smart_enabled=true` (e.g. firmware update, manual change), sync skipped `enableNodePositionBroadcastReady()` — smart broadcast silently extends interval to 240–480 s on stationary devices, which exceeds `POSITION_FRESHNESS_SECONDS=120 s` threshold and causes periodic grey.
**Result:** fixed — (1) `POSITION_FRESHNESS_SECONDS` raised from 120 s to 300 s; (2) `needsBroadcastWrite` now also triggers when `position_broadcast_smart_enabled=true` while GPS broadcast is desired; (3) `IsPositionSmartBroadcastEnabledUseCase` added; (4) GPS timestamp fallback in `AndroidMeshLocationManager` retained as defence-in-depth.

---
