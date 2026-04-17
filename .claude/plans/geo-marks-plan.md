# Plan: Geo Marks — Points and Tracks Creation, Sending, Receiving, Map Rendering

**Date**: 2026-04-17
**Status**: Approved

---

## Summary

Allows users to place geo marks (points and tracks) on the map, send them to the shared Meshtastic channel as Waypoint packets, receive marks from other nodes, and display all marks persistently on the map. The creation tool is toggled from the HUD "marks" button; gestures on the map drive the draft-mark workflow. Transport encoding uses the MeshTactics Waypoint format specified in `.claude/specs/geo-series-sharing.md`.

---

## Scope

**In scope (MVP):**
- Point and Track types only
- Fixed color and icon (no user selection)
- Mark tool toggle via HUD button (left column)
- Single tap → add draft point; draft points form a track when ≥2
- Long-tap on draft point → context menu: Send / Delete
- Double-tap on map → add point + send immediately
- Tool deactivation silently discards unsent drafts
- Received marks (from other nodes) rendered persistently on map
- SQLDelight storage for received marks (survives app restart)
- Waypoint encoding per `.claude/specs/geo-series-sharing.md` (namespace=0x4D, type=Point/Track, fixed color)

**Out of scope:**
- Polygon, Primitive, Package types
- Color/icon picker UI
- Named marks (title input dialog)
- Mark expiration UI
- Editing sent marks
- KMZ/KML "share layer" integration
- Mark categories or filtering

---

## Architecture Overview

### Existing stubs to revise

`domain/marker/model/MarkerModel.kt`, `TrackModel.kt`, and `domain/marker/repository/MarkerRepository.kt` are placeholder stubs with no implementation. They must be **replaced** by the models and repository defined in this plan.

### Domain model unification

Point and Track are unified into a single `GeoMarkModel` — matching the single-Waypoint transport. This avoids dual repository methods and simplifies state management.

```
domain/marker/model/
  GeoMarkModel.kt        — unified model (type: GeoMarkType; points: List<GeoPoint>)
  GeoMarkType.kt         — enum { POINT, TRACK }
  GeoPoint.kt            — already exists, keep as-is
```

`MarkerModel.kt` and `TrackModel.kt` are **deleted** (no current usage outside the domain).

### Transport adapter (data layer only)

Encoding and decoding the Meshtastic Waypoint ↔ `GeoMarkModel` is isolated in `GeoMarkWaypointAdapter`. It is the only class that imports `org.meshtastic.proto.Waypoint`.

Key encoding details (per spec):
- `icon` packed uint32: `(0x4D << 24) | (type << 16) | (color << 8) | variant`
- `description`: `"MT1:<base64(payload)>"` where payload for Track contains `count: u8, ends: u8, points: []{x: int16, y: int16}`
- Anchor = first point (in `lat/lon`); remaining points encoded as local offsets (int16 metres from anchor)
- MAX_POINTS = 27 (excluding anchor); validation before send

### Sending path

```
SendGeoMarkUseCase
  → GeoMarkRepository.sendGeoMark(GeoMarkModel)
  → GeoMarkRepositoryImpl
      → GeoMarkWaypointAdapter.encode(GeoMarkModel): DataPacket
      → CommandSender.sendData(dataPacket)          // mesh layer
      → geoMarkDao.insertOrReplace(geoMarkEntity)   // SQLDelight, persist sent marks
```

### Receiving path

```
MeshDataHandlerImpl.handleWaypoint()         // already stores DataPacket in Room (mesh layer)
  → PacketRepository.getWaypoints(): Flow    // Room DB
  → GeoMarkRepositoryImpl.observeGeoMarks()
      → GeoMarkWaypointAdapter.decode(DataPacket): GeoMarkModel?  (null if not MT1 format)
  → ObserveGeoMarksUseCase → MainViewModel → MainUiState.geoMarks
  → MapLibreLayer renders received marks
```

The mesh layer's Room DB is the source-of-truth for received Waypoints. The SQLDelight table mirrors received marks for the tactical layer (cross-layer isolation).

### Draft mark state (presentation-only)

Draft marks (added but not yet sent) live **only in `MainViewModel`** as `List<GeoPoint>`. They are never persisted — deactivating the tool discards them silently. This keeps them out of domain/data entirely.

### Gesture handling

`MaplibreMap` exposes `onMapClick` and `onMapLongClick` via `MapClickHandler = (Position, DpOffset) -> ClickResult`. Double-tap is implemented in `MainViewModel` via a coroutine debounce:

- On `onMapClick`: post a single-tap coroutine job with `DOUBLE_TAP_WINDOW_MS` delay (300ms)
- If a second `onMapClick` arrives within the window: cancel the job, execute double-tap action instead
- When tool is active: disable MapLibre's native double-tap-to-zoom via `GestureOptions(doubleTapToZoom = false)` passed to `MapLibreLayer`

Long-tap on a **draft point** (vs. arbitrary map position) is detected by proximity: in `onMapLongClick`, check if the tapped `Position` is within `DRAFT_POINT_TOUCH_RADIUS_DP` of any pending draft point.

---

## Phase Plan

### Phase 0 — Research *(skip — domain is understood from spec)*

The transport encoding is fully specified in `.claude/specs/geo-series-sharing.md`. MapLibre gesture API is confirmed: `onMapClick` + `onMapLongClick` are available; `GestureOptions` allows disabling double-tap zoom. No external research needed.

---

### Phase 1 — Architecture Design

**Goal**: Approved architecture — layers, interfaces, data flow, SQLDelight schema.

**Tasks**:
1. Design `GeoMarkModel`, `GeoMarkType` in `domain/marker/model/`
2. Design `GeoMarkRepository` interface in `domain/marker/repository/`
3. Design use case signatures: `ObserveGeoMarksUseCase`, `SendGeoMarkUseCase`
4. Design `GeoMarkWaypointAdapter` contract (encode/decode signatures, MAX_POINTS/MAX_PAYLOAD validation)
5. Design SQLDelight schema `geo_marks` table
6. Design `MainUiState` additions and `MainViewModel` mark-tool state shape

**Skill**: `/architect feature: Geo Marks — domain model, repository, adapter, SQLDelight schema, ViewModel state`

**Output**: architecture plan (this document extended with detailed signatures + file tree)

> **Token checkpoint**: run `/compact` after Phase 1 before proceeding.

---

### Phase 2 — UI / Icon Design

**Goal**: HUD button icon approved; map rendering style approved.

**Tasks**:
1. Create HUD "marks" button icon (`ic_marks_tool.xml`) — invoke `/icon-designer`
2. Define rendering style for draft points: color, circle radius, line style
3. Define rendering style for received marks: color, icon, line style
4. Confirm that draft and received marks are visually distinct from node markers

**Skill**: `/icon-designer create: marks-tool HUD button`; `/ui-designer` for map rendering tokens.

**Output**: `ic_marks_tool.xml`; approved rendering spec (colors, sizes — documented in phase)

---

### Phase 3 — Implementation

**Goal**: Buildable, working feature across all layers.

**Order**: Domain → Data → DI → Presentation

#### 3.1 Domain layer

Files to create/modify:

```
app/domain/marker/model/
  GeoMarkModel.kt        ← new; replace MarkerModel.kt + TrackModel.kt
  GeoMarkType.kt         ← new
  GeoPoint.kt            ← keep, no change

app/domain/marker/repository/
  GeoMarkRepository.kt   ← new; replace MarkerRepository.kt

app/domain/marker/usecase/
  ObserveGeoMarksUseCase.kt   ← new
  SendGeoMarkUseCase.kt       ← new (params: GeoMarkModel)
```

Delete: `MarkerModel.kt`, `TrackModel.kt`, `MarkerRepository.kt` (stubs, no existing callers).

#### 3.2 Data layer

```
app/data/marker/
  adapter/
    GeoMarkWaypointAdapter.kt  ← encode + decode; imports Waypoint proto
  repository/
    GeoMarkRepositoryImpl.kt   ← implements GeoMarkRepository
```

`GeoMarkRepositoryImpl` dependencies:
- `PacketRepository` (mesh layer) — `getWaypoints(): Flow<List<DataPacket>>`
- `CommandSender` (mesh layer) — `sendData(DataPacket)`
- `GeoMarkWaypointAdapter` — encode/decode
- `GeoMarkDao` (SQLDelight) — persistence

SQLDelight schema (add to `shared/` or `app/` SQLDelight sources):

```sql
CREATE TABLE geo_mark (
    id TEXT NOT NULL PRIMARY KEY,
    waypoint_id INTEGER NOT NULL DEFAULT 0,
    type TEXT NOT NULL,              -- 'POINT' | 'TRACK'
    points_json TEXT NOT NULL,       -- JSON array of {lat, lon}
    author_node_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER,
    is_self INTEGER NOT NULL DEFAULT 0
);
```

#### 3.3 DI (Koin)

Add `GeoMarkDataModule`:
```kotlin
val geoMarkDataModule = module {
    single { GeoMarkWaypointAdapter() }
    single<GeoMarkRepository> { GeoMarkRepositoryImpl(get(), get(), get(), get()) }
    single { ObserveGeoMarksUseCase(get()) }
    single { SendGeoMarkUseCase(get()) }
}
```

#### 3.4 Presentation layer

**`MainUiState`** additions:
```kotlin
val geoMarks: ImmutableList<GeoMarkModel> = persistentListOf(),
val markToolActive: Boolean = false,
val pendingMarkPoints: ImmutableList<GeoPoint> = persistentListOf(),
```

**`MainViewModel`** additions:
- `observeGeoMarks` use case injected; collects into `uiState.geoMarks`
- `toggleMarkTool()` — flip `markToolActive`; if deactivating: clear `pendingMarkPoints`
- `onMapSingleTap(lat: Double, lon: Double)` — if tool active: append to `pendingMarkPoints`
- `onMapLongTap(lat: Double, lon: Double)` — if tool active: find nearest pending point, emit `GeoMarkContextMenuEvent(pointIndex)`
- `onMapDoubleTap(lat: Double, lon: Double)` — add point + send immediately via `SendGeoMarkUseCase`
- `sendPendingMark()` — send current `pendingMarkPoints` via use case; clear pending
- `deletePendingPoint(index: Int)` — remove from list
- Double-tap debounce: `Job?` + `DOUBLE_TAP_WINDOW_MS = 300L`

**`MapLibreLayer`** additions:
- New parameters: `onMapClick: (Position) -> Unit`, `onMapLongClick: (Position) -> Unit`, `gestureOptions: GestureOptions`
- New parameters: `geoMarks: ImmutableList<GeoMarkModel>`, `pendingMarkPoints: ImmutableList<GeoPoint>`
- Forward `onMapClick` / `onMapLongClick` to `MaplibreMap`
- Pass `gestureOptions` to `MaplibreMap` (used to disable double-tap zoom when tool active)
- Render draft marks: `CircleLayer` for points + `LineLayer` for connecting line (pending, distinct color)
- Render received marks: `CircleLayer` for points + `LineLayer` for tracks + `SymbolLayer` for point label

**`HudConfigFactory`** / `buildHudConfig()`:
- Add "marks" button to left column with `selected = state.markToolActive`
- `onClick = { viewModel.toggleMarkTool() }`

**`MainScreen`**:
- Wire `onMapClick` → `viewModel.onMapSingleTap()`
- Context menu composable on `GeoMarkContextMenuEvent`

---

### Phase 4 — Testing

**Goal**: Feature verified at unit + integration level.

**Tasks**:
1. Unit: `GeoMarkWaypointAdapter` — encode→decode roundtrip for Point and Track; boundary (MAX_POINTS=27, MAX_PAYLOAD=145)
2. Unit: `MainViewModel` — `toggleMarkTool`, `onMapSingleTap`, `deletePendingPoint`, double-tap debounce
3. Integration: `GeoMarkRepositoryImpl` + in-memory SQLDelight DB + mock `PacketRepository`

**Skill**: direct coding; `/tester` for scaffolding if needed.

---

### Phase 5 — Integration Review

**Goal**: Confirm no Clean Architecture violations.

**Tasks**:
- Check `GeoMarkRepositoryImpl` — does not import `org.meshtastic.proto.*` directly (only via adapter)
- Check `MapLibreLayer` — no domain imports except models passed as parameters
- Check `MainViewModel` — no direct mesh layer imports

**Skill**: `/architect review: data/marker/, domain/marker/, presentation/feature/main/`

---

### Phase 6 — Skill Update Review

- **`/architect`**: document `GeoMarkWaypointAdapter` isolation pattern (proto imports only in adapter); double-tap debounce pattern in ViewModel
- **`/ui-designer`**: draft vs. received mark color tokens; context menu pattern on map
- **`/icon-designer`**: no new icon style decisions beyond what is produced in Phase 2
- **`/tester`**: adapter roundtrip test pattern (encode→decode) — add if not already documented
- **`/planner`**: no methodology changes needed

---

### Phase 6b — Docs & Memory Update

- Update `CLAUDE.md` status table: `Маркеры / заметки` → `In Progress`
- Create `.claude/docs/geo-marks.md`
- Move this file to `.claude/archive/geo-marks-plan.md`, delete from `plans/`
- Update memory: mark feature in progress in project state
- Record token cost in archive Change Log

---

### Phase 7 — Commit Preparation

- Stage by name (never `git add -A`)
- Commit message in Russian, no `Co-Authored-By`
- Wait for user confirmation before `git commit`

---

## Coordination Map

```
Phase 0: skip (domain fully understood)
Phase 1: /architect feature: Geo Marks domain model + adapter + schema → [/compact]
Phase 2: /icon-designer create: marks-tool HUD button
         /ui-designer: map rendering style for draft + received marks
Phase 3: [direct coding] domain → data → DI → presentation → /simplify
Phase 4: [direct coding — tests]
Phase 5: /architect review: data/marker/, domain/marker/, presentation/feature/main/
Phase 6: [skill update review — architect, ui-designer, icon-designer, tester, planner]
Phase 6b: [docs, CLAUDE.md, archive plan, memory]
Phase 7: [stage by name] → [propose message] → [wait confirmation] → git commit
```

---

## Resolved Decisions

1. **Expiry (`expire` field)**: fixed TTL = **8 hours** (`nowSeconds + 8 * 3600`). TODO: make configurable in Settings in a future iteration.

2. **`locked_to` field**: always set to the sender's own node ID, read from `NodeRepository.ourNodeInfo` at send time.

3. **Context menu UI**: floating `DropdownMenu` anchored to the tap position (next to the point). TODO: revisit if it clips off-screen in edge cases.

4. **Long-tap proximity**: responds only when tap lands within **24dp** of a draft point. Tune in manual testing if needed.

5. **SQLDelight placement**: table goes in `shared/` — consistent with KMZ/KML overlay storage.

6. **Double-tap zoom**: when the mark tool is active, `doubleTapToZoom` is **disabled** in `GestureOptions`. Re-enabled on tool deactivation. Verify `GestureOptions` API field name before implementation.

## Open Questions

- **`GestureOptions` API**: confirm the exact field name for disabling double-tap zoom in maplibre-compose 0.12.1 before Phase 3.

---

## Target File Structure

```
app/
├── domain/
│   └── marker/
│       ├── model/
│       │   ├── GeoPoint.kt            ✅ keep
│       │   ├── GeoMarkModel.kt        ⬜ new (replaces MarkerModel + TrackModel)
│       │   └── GeoMarkType.kt         ⬜ new
│       ├── repository/
│       │   └── GeoMarkRepository.kt   ⬜ new (replaces MarkerRepository)
│       └── usecase/
│           ├── ObserveGeoMarksUseCase.kt  ⬜ new
│           └── SendGeoMarkUseCase.kt       ⬜ new
├── data/
│   └── marker/
│       ├── adapter/
│       │   └── GeoMarkWaypointAdapter.kt  ⬜ new
│       └── repository/
│           └── GeoMarkRepositoryImpl.kt   ⬜ new
└── presentation/
    └── feature/
        └── main/
            ├── MainUiState.kt         ← extend (geoMarks, markToolActive, pendingMarkPoints)
            ├── MainViewModel.kt       ← extend (mark tool logic, gesture handlers)
            └── osd/
                ├── MapLibreLayer.kt   ← extend (gesture callbacks, geo mark rendering)
                └── HudConfigFactory.kt ← extend (marks button)

shared/ (or app/) SQLDelight:
  └── geo_marks.sq                    ⬜ new

DELETE:
  domain/marker/model/MarkerModel.kt     (stub, no callers)
  domain/marker/model/TrackModel.kt      (stub, no callers)
  domain/marker/repository/MarkerRepository.kt (stub, no callers)
```

---

## Change Log

- 2026-04-17: created
- 2026-04-17: all open questions resolved; status → Approved
