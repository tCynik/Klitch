# Geo Marks — Points and Tracks

**Date**: 2026-04-18
**Plan archive**: `.claude/archive/geo-marks-plan.md`

---

## Overview

Feature allows users to place geo marks (points and tracks) on the map, send them to the shared Meshtastic channel as Waypoint packets, receive marks from other nodes, and display all marks persistently on the map. Transport format is MT1 (MeshTactics Waypoint, namespace `0x4D`), specified in `.claude/specs/geo-series-sharing.md`.

---

## File Structure

```
app/
├── domain/marker/
│   ├── model/
│   │   ├── GeoMarkModel.kt       — unified model (type + points list)
│   │   ├── GeoMarkType.kt        — enum { POINT, TRACK }
│   │   └── GeoPoint.kt           — lat/lon (shared with node markers)
│   ├── repository/
│   │   └── GeoMarkRepository.kt  — observeGeoMarks(): Flow / sendGeoMark(GeoMarkModel)
│   └── usecase/
│       ├── ObserveGeoMarksUseCase.kt   — FlowUseCase<NoParams, List<GeoMarkModel>>
│       └── SendGeoMarkUseCase.kt       — UseCase<GeoMarkModel, Unit>
├── data/marker/
│   ├── adapter/
│   │   └── GeoMarkWaypointAdapter.kt   — encode/decode; ONLY class importing Waypoint proto
│   └── repository/
│       └── GeoMarkRepositoryImpl.kt    — combines PacketRepository + SQLDelight + adapter
└── presentation/feature/main/
    ├── MainUiState.kt            — geoMarks, markToolActive, pendingMarkPoints
    ├── MainViewModel.kt          — mark tool logic, gesture handlers, double-tap debounce
    └── osd/
        ├── MapLibreLayer.kt      — gesture callbacks + geo mark rendering layers
        └── HudConfigFactory.kt   — "метки" button in left column (row 3)

shared/src/commonMain/sqldelight/.../data/local/
└── GeoMark.sq                    — geo_mark table + selectAll/selectById/selectSelfIds/insert/deleteById/deleteExpired
```

---

## Domain Model

```kotlin
data class GeoMarkModel(
    val id: String,            // UUID; stable key for UI + SQLDelight PK
    val waypointId: Int,       // Meshtastic waypoint ID (0 for new, filled on decode)
    val type: GeoMarkType,
    val points: List<GeoPoint>, // points[0] = anchor; points[1..N] = track extras
    val authorNodeId: String,  // "!ab12cd34"; placeholder "" on send, filled by repo
    val createdAt: Long,       // Unix seconds
    val expiresAt: Long?,      // Unix seconds; null = no expiry
    val isSelf: Boolean,       // true if sent by this device
)
```

---

## Transport: GeoMarkWaypointAdapter

The **only** class in the project that imports `org.meshtastic.proto.Waypoint`.

### Icon packing

```
icon = (0x4D << 24) | (type << 16) | (color << 8) | variant
```

- `0x4D` = namespace byte (MeshTactics)
- type: `0` = POINT, `1` = TRACK
- color/variant: `0` for MVP (fixed)

### Description encoding

- Point: `"MT1:"` (no payload)
- Track: `"MT1:<base64(payload)>"` where payload = `[count: u8, ends: u8, {x: i16, y: i16}...]`
  - Anchor = `lat_i / 1e7`, `lon_i / 1e7` from `Waypoint.latitude_i / longitude_i`
  - Extra points encoded as int16 metre offsets from anchor (cos-corrected longitude)

### Constants

| Constant | Value |
|---|---|
| `MAX_POINTS` | 27 (extra points beyond anchor) |
| `MAX_PAYLOAD_BYTES` | 145 |
| `EXPIRE_TTL_SECONDS` | 28800 (8 hours) |

### `encode(mark, ourNodeNum, ourNodeId, nowSeconds): DataPacket`

- `Waypoint.locked_to = ourNodeNum` (sender's node number)
- `Waypoint.expire = nowSeconds + 8h`
- Returns `DataPacket(to = BROADCAST, channel = 0, waypoint = ...)`
- `authorNodeId` in the model is a placeholder — the real value is read from `MeshNetworkRepository` in `GeoMarkRepositoryImpl.sendGeoMark`

### `decode(packet, selfIds): GeoMarkModel?`

- Returns `null` if not a valid MT1 waypoint (wrong namespace byte or missing prefix)
- `isSelf = markId in selfIds` (selfIds come from SQLDelight `selectSelfIds`)

---

## Data Flow

### Send

```
ViewModel.sendPendingMark() / onMapDoubleClick()
  → SendGeoMarkUseCase(GeoMarkModel)
  → GeoMarkRepositoryImpl.sendGeoMark()
      → meshNetwork.observeOurNode().first()   // get sender node num + id
      → GeoMarkWaypointAdapter.encode()
      → CommandSender.sendData(DataPacket)     // broadcast
      → geoMarkQueries.insert(isSelf=1)        // persist as self-sent
```

### Receive

```
PacketRepository.getWaypoints(): Flow<List<DataPacket>>   // mesh layer Room DB
  + geoMarkQueries.selectSelfIds(): Flow<List<String>>    // SQLDelight
  → combine → GeoMarkWaypointAdapter.decode()
  → ObserveGeoMarksUseCase → MainViewModel → MainUiState.geoMarks
  → MapLibreLayer renders
```

---

## SQLDelight Schema

```sql
CREATE TABLE geo_mark (
    id             TEXT    NOT NULL PRIMARY KEY,
    waypoint_id    INTEGER NOT NULL DEFAULT 0,
    type           TEXT    NOT NULL,              -- 'POINT' | 'TRACK'
    points_json    TEXT    NOT NULL,              -- JSON [{lat:..., lon:...}, ...]
    author_node_id TEXT    NOT NULL,
    created_at     INTEGER NOT NULL,              -- Unix seconds
    expires_at     INTEGER,                       -- Unix seconds; NULL = no expiry
    is_self        INTEGER NOT NULL DEFAULT 0     -- 1 = sent by this device
);
```

Queries: `selectAll`, `selectById`, `selectSelfIds`, `insert`, `deleteById`, `deleteExpired`.

---

## Presentation

### MainUiState additions

```kotlin
val geoMarks: ImmutableList<GeoMarkModel>      // received + self-sent marks
val markToolActive: Boolean                     // is the mark tool toggled on
val pendingMarkPoints: ImmutableList<GeoPoint> // draft points, never persisted
```

### MainViewModel — mark tool

| Method | Behaviour |
|---|---|
| `toggleMarkTool()` | flip `markToolActive`; deactivation cancels `doubleTapJob` + clears `pendingMarkPoints` |
| `onMapClick(lat, lon)` | if tool active: debounce (300ms); single-tap → append to pending; double-tap → send POINT immediately |
| `onMapDoubleClick(lat, lon)` | cancel pending job; build GeoMarkModel(POINT) + `sendGeoMark` |
| `onMapLongClick(lat, lon, screenX, screenY)` | proximity check (30m radius) against pending points; emit `GeoMarkContextMenuEvent` if hit |
| `sendPendingMark()` | build POINT or TRACK from `pendingMarkPoints`; send + clear |
| `deletePendingPoint(index)` | remove from `pendingMarkPoints` by index |

### Double-tap debounce pattern

```kotlin
private var doubleTapJob: Job? = null

fun onMapClick(lat: Double, lon: Double) {
    if (doubleTapJob?.isActive == true) {
        doubleTapJob?.cancel()
        doubleTapJob = null
        onMapDoubleClick(lat, lon)   // second tap — treat as double
        return
    }
    doubleTapJob = viewModelScope.launch {
        delay(DOUBLE_TAP_WINDOW_MS)  // 300ms
        doubleTapJob = null
        // timeout elapsed without second tap → single tap
        appendPendingPoint(lat, lon)
    }
}
```

Key: cancelling `doubleTapJob` **is** the signal that a double-tap occurred — no extra flag needed. Always cancel on `toggleMarkTool(deactivate)`.

### MapLibreLayer — mark tool

- `GestureOptions(isDoubleTapEnabled = false, isQuickZoomEnabled = false)` when `markToolActive = true`
- `onMapClick` / `onMapLongClick` forwarded from `MaplibreMap` callbacks → ViewModel
- Draft marks: `CircleLayer` (hollow cyan `0xFF29B6F6`) + `LineLayer` (cyan)
- Received POINT marks: solid `CircleLayer` (`0xFF1E88E5`)
- Received TRACK marks: `LineLayer` + anchor `CircleLayer` (`0xFF1E88E5`)

### HUD

Mark tool toggle: left column, row 3 — `ic_marks_tool`, `selected = state.markToolActive`, `onClick = toggleMarkTool()`.

---

## Resolved Decisions

| Decision | Resolution |
|---|---|
| Expiry TTL | 8 hours, fixed. Make configurable in Settings in a future iteration |
| `locked_to` field | sender's own `nodeNum`, read from `MeshNetworkRepository` at send time |
| Context menu | `DropdownMenu` anchored to tap screen coordinates |
| Long-tap proximity | 30m radius (approximate, not dp-scaled) |
| SQLDelight location | `shared/` module — consistent with KMZ/KML overlay storage |
| Double-tap zoom | Disabled via `GestureOptions` when tool is active; re-enabled on deactivation |
| `android.util.Base64` | Used in `GeoMarkWaypointAdapter` — acceptable in `app` module; replace with `kotlin.io.encoding.Base64` if moved to `shared` |
