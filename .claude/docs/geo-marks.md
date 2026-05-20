# Geo Marks — Points and Tracks

**Date**: 2026-05-19
**Plan archives**: `.claude/archive/geo-marks-plan.md`, `.claude/archive/geo-marks-sheet.md`

---

## Overview

Feature allows users to create and place geo marks (points and tracks) on the map via a persistent bottom sheet, send them to the shared Meshtastic channel as Waypoint packets, receive marks from other nodes, and display all marks on the map. Transport format is MT1 (MeshTactics Waypoint, namespace `0x4D`). Marks carry user label, color, shape, and track end-type; all fields are persisted between sessions via DataStore.

---

## File Structure

```
app/
├── domain/marker/
│   ├── model/
│   │   ├── GeoMarkModel.kt              — unified mark model
│   │   ├── GeoMarkType.kt               — enum { POINT, TRACK }
│   │   ├── GeoMarkShape.kt              — enum { CIRCLE, SQUARE, TRIANGLE }
│   │   ├── GeoMarkColor.kt              — 16-slot ARGB Int palette; no Compose dependency
│   │   ├── TrackEndType.kt              — enum { NONE, SMALL_FILLED_CIRCLE, LARGE_EMPTY_CIRCLE, ARROW }
│   │   ├── GeoMarkFormPreferences.kt    — @Serializable prefs stored in DataStore
│   │   ├── GeoMarkPreset.kt             — @Serializable saved combo (id + displayName + prefs)
│   │   └── GeoPoint.kt                  — lat/lon
│   ├── repository/
│   │   ├── GeoMarkRepository.kt         — observeGeoMarks / sendGeoMark / persistReceived / deleteExpired
│   │   └── GeoMarkPreferencesRepository.kt — observePreferences / observePresets / savePreferences / addPreset
│   └── usecase/
│       ├── ObserveGeoMarksUseCase.kt
│       ├── SendGeoMarkUseCase.kt
│       ├── DeleteExpiredGeoMarksUseCase.kt
│       └── IngestReceivedGeoMarksUseCase.kt
├── data/marker/
│   ├── adapter/
│   │   └── GeoMarkWaypointAdapter.kt    — encode/decode; only class importing Waypoint proto
│   └── repository/
│       └── GeoMarkRepositoryImpl.kt     — PacketRepository + SQLDelight + adapter; channel override
├── data/markprefs/
│   ├── GeoMarkPrefsDataSource.kt        — DataStore read/write; preset eviction (max 10)
│   └── GeoMarkPreferencesRepositoryImpl.kt — implements GeoMarkPreferencesRepository
└── presentation/feature/main/
    ├── GeoMarksFormState.kt             — internal ViewModel form state (not exposed directly)
    ├── MainUiState.kt                   — geoMarks, markToolActive, pendingMarkPoints
    ├── MainViewModel.kt                 — _formState StateFlow, mark tool logic, gesture handlers
    ├── MainScreen.kt                    — GeoMarksSheet wired at BottomEnd; no floating send panel
    └── osd/
        ├── GeoMarksSheet.kt             — persistent non-modal bottom sheet composable
        ├── MapLibreLayer.kt             — gesture callbacks + geo mark rendering layers
        └── models/
            ├── GeoMarksSheetUiState.kt  — all sheet data + callbacks bundled (MenuDrawer pattern)
            └── GeoMarkAddressee.kt      — contourId + displayName for адресат dropdown

shared/src/commonMain/sqldelight/.../data/local/
└── GeoMark.sq                           — geo_mark table; 13 columns including color/name/track_end_type/shape
```

---

## Domain Model

```kotlin
data class GeoMarkModel(
    val id: String,              // UUID; stable key for UI + SQLDelight PK
    val waypointId: Int,         // MT waypoint ID (0 for new; filled on decode)
    val type: GeoMarkType,       // POINT | TRACK
    val points: List<GeoPoint>,  // points[0] = anchor; points[1..N] = track extras
    val authorNodeId: String,    // "!ab12cd34"
    val createdAt: Long,         // Unix seconds
    val expiresAt: Long?,        // Unix seconds; null = no expiry
    val isSelf: Boolean,         // true if sent by this device
    val color: Int = 0,          // index into GeoMarkColor.palette (0–15)
    val name: String = "",       // user label; maps to Waypoint.name
    val trackEndType: TrackEndType = TrackEndType.NONE,
    val shape: GeoMarkShape = GeoMarkShape.CIRCLE,
)
```

### GeoMarkColor

16-slot ARGB Int palette. No Compose dependency — domain stays framework-free.

```kotlin
object GeoMarkColor {
    val palette: List<Int>  // 0xFFRRGGBB.toInt() values, index 0–15
    fun colorAt(index: Int): Int   // returns ARGB Int; use Color(argb) in presentation
    fun indexOf(argb: Int): Int
}
```

Presentation call sites wrap with `Color(GeoMarkColor.colorAt(index))`.
`MapLibreLayer.markColorHex()` extracts channels via bit ops: `(argb shr 16) and 0xFF` etc.

### GeoMarkShape

```kotlin
enum class GeoMarkShape { CIRCLE, SQUARE, TRIANGLE }
```

Stored in SQLDelight as ordinal. Rendered in `ShapeIcon` Canvas composable (filled or stroke).

### TrackEndType

```kotlin
enum class TrackEndType(val ends: Byte) {
    NONE(0), SMALL_FILLED_CIRCLE(1), LARGE_EMPTY_CIRCLE(2), ARROW(3)
}
```

MVP UI shows NONE and ARROW only. Packed into description payload byte 1 at encode; extracted at decode. `fromByte(b: Byte)` for decode.

---

## Transport: GeoMarkWaypointAdapter

The **only** class importing `org.meshtastic.proto.Waypoint`.

### Icon packing

```
icon = (0x4D << 24) | (typeCode << 16) | (color << 8) | variant
```

- `0x4D` = MeshTactics namespace
- typeCode: `0` = POINT, `1` = TRACK
- color: index 0–15 into `GeoMarkColor.palette`
- variant: `0` (reserved)

### Description format

- Point: `"MT1:"` (no payload)
- Track: `"MT1:<base64(payload)>"` where payload = `[count: u8, ends: u8, {x: i16, y: i16}…]`
  - Extra points as int16 metre offsets from anchor (cos-corrected lon)

### `encode(mark, ourNodeNum, ourNodeId, nowSeconds): DataPacket`

Uses `mark.color`, `mark.name`, `mark.trackEndType.ends`, `mark.expiresAt`. Returns `DataPacket(channel=0)` — channel override is a repo concern.

### `decode(packet, selfIds): GeoMarkModel?`

Extracts `colorIndex = (icon ushr 8) and 0xF`, `endsByte` from payload byte 1, `name` from `waypoint.name`. Populates `GeoMarkModel.color`, `.name`, `.trackEndType`.

### Constants

| Constant | Value |
|---|---|
| `MAX_POINTS` | 27 extra points beyond anchor |
| `MAX_PAYLOAD_BYTES` | 145 |
| `EXPIRE_TTL_SECONDS` | 28800 (8h, fallback when `expiresAt` is null) |

---

## Data Flow

### Send

```
ViewModel.sendPendingMark()
  → builds GeoMarkModel from GeoMarksFormState (type, color, shape, name, ttl, trackEndType)
  → SendGeoMarkUseCase(SendGeoMarkParams(mark, contourId?, localOnly))
  → GeoMarkRepositoryImpl.sendGeoMark()
      → if localOnly: geoMarkQueries.insert() only
      → else: adapter.encode() → packet.channel = resolvedSlot → commandSender.sendData()
              → geoMarkQueries.insert(isSelf=1)
```

### Receive

```
IngestReceivedGeoMarksUseCase (launched in ViewModel.init)
  PacketRepository.getWaypoints(): Flow<DataPacket>
  + geoMarkQueries.selectSelfIds()
  → GeoMarkWaypointAdapter.decode()
  → geoMarkQueries.insertReceived()
  → ObserveGeoMarksUseCase → MainUiState.geoMarks → MapLibreLayer
```

---

## SQLDelight Schema

```sql
CREATE TABLE geo_mark (
    id                 TEXT    NOT NULL PRIMARY KEY,
    waypoint_id        INTEGER NOT NULL DEFAULT 0,
    type               TEXT    NOT NULL,              -- 'POINT' | 'TRACK'
    points_json        TEXT    NOT NULL,
    author_node_id     TEXT    NOT NULL,
    created_at         INTEGER NOT NULL,
    expires_at         INTEGER,
    is_self            INTEGER NOT NULL DEFAULT 0,
    logical_channel_id TEXT    NOT NULL DEFAULT '',
    color              INTEGER NOT NULL DEFAULT 0,    -- GeoMarkColor.palette index
    name               TEXT    NOT NULL DEFAULT '',
    track_end_type     INTEGER NOT NULL DEFAULT 0,    -- TrackEndType.ends byte
    shape              INTEGER NOT NULL DEFAULT 0     -- GeoMarkShape.ordinal
);
```

Queries: `selectAll`, `selectById`, `selectSelfIds`, `selectAllForChannel`, `insert`, `insertReceived`, `deleteById`, `deleteExpired`.

---

## DataStore Persistence

`data/markprefs/` package owns all form state persistence.

`GeoMarkFormPreferences` (domain, `@Serializable`):
- `selectedType: String` (enum name)
- `selectedColor: Int` (palette index)
- `selectedShape: String` (enum name)
- `selectedTrackEndType: Int` (TrackEndType.ends byte value — known inconsistency with String approach for other fields)
- `selectedTtlSeconds: Long`
- `markName: String`
- `nameCounter: Int`
- `selectedContourId: String`

`GeoMarkPreset` (domain, `@Serializable`): `id + displayName + prefs: GeoMarkFormPreferences`

`GeoMarkPreferencesRepository` (domain interface):
- `observePreferences(): Flow<GeoMarkFormPreferences>`
- `observePresets(): Flow<List<GeoMarkPreset>>`
- `suspend fun savePreferences(prefs: GeoMarkFormPreferences)`
- `suspend fun addPreset(preset: GeoMarkPreset)` — evicts oldest if count > 10

`GeoMarkPrefsDataSource`: Preferences DataStore. Presets stored as JSON list in single key `geomark_presets_json`.

---

## Presentation

### GeoMarksFormState (ViewModel-internal)

Separate `_formState: MutableStateFlow<GeoMarksFormState>` in `MainViewModel`. Not part of `MainUiState`.

```kotlin
data class GeoMarksFormState(
    val isSheetVisible: Boolean,
    val isCollapsed: Boolean,
    val selectedType: GeoMarkType,
    val selectedColor: Int,        // default 4 = Red
    val selectedShape: GeoMarkShape,
    val selectedTrackEndType: TrackEndType,
    val selectedTtlSeconds: Long,
    val markName: String,
    val nameCounter: Int,
    val selectedContourId: String,
    val wasAddresseeExplicitlySelected: Boolean,
    val availableContours: ImmutableList<GeoMarkAddressee>,
    val savedPresets: ImmutableList<GeoMarkPreset>,
)
```

### GeoMarksSheetUiState

Derived StateFlow combining `_uiState + _formState`. All callbacks bundled (MenuDrawerUiState pattern).

### MainViewModel — mark tool

| Method | Behaviour |
|---|---|
| `toggleGeoMarksSheet()` | open/close sheet; opening activates mark tool |
| `closeGeoMarksSheet()` | close sheet + deactivate mark tool + clear pending points |
| `toggleMarkTool()` | direct tool toggle (HUD mark-tool shortcut); does not affect sheet visibility |
| `setMarkType/Color/Shape/TrackEndType/Ttl/MarkName/NameCounter/Addressee()` | update `_formState`, persist to DataStore |
| `applyPreset(preset)` | restore all form fields from preset |
| `sendPendingMark()` | build mark from form state; increment counter; persist prefs; save preset |
| `onMapClick(lat, lon)` | debounce 300ms; single-tap → add pending point (POINT replaces, TRACK appends) |
| `onMapDoubleClick(lat, lon)` | quick-drop POINT with defaults; bypasses sheet form state |
| `onMapLongClick(lat, lon, screenX, screenY)` | proximity check (30m); emit `GeoMarkContextMenuEvent` |
| `clearPendingPoints()` | clear `pendingMarkPoints` from `_uiState` |
| `deletePendingPoint(index)` | remove by index |

### Name counter rules

- Resets to 1 on `setMarkName()` (text change)
- Continues from current on `setNameCounter()` (manual edit)
- Auto-increments after each successful `sendPendingMark()`

### Addressee logic

`availableContours` = active contours via `ObserveContoursUseCase` + `GeoMarkAddressee("__local__", "Хранилище")`.

Selection priority:
1. Previously explicit selection still in list → keep it
2. Connected + contours present → first active contour
3. Fallback → Хранилище (local only)

### GeoMarksSheet composable

Non-modal bottom sheet. `AnimatedVisibility(slideInVertically + fadeIn)` at `Alignment.BottomEnd` in `MainScreen`.

**Header**: Edit icon | ShapeIcon with fill color | `{name} {counter}/{ttlShort}` | collapse | close

**Body** (hidden when collapsed):
- Type dropdown (POINT, TRACK; POLYGON/PRIMITIVE disabled items)
- Shape + Color dropdowns (side by side with type)
- Type-specific section: TRACK shows end-type dropdown + `точек: N / 27`; POINT shows nothing
- Name field + counter field + TTL dropdown (9 options: 15m … 3 days)
- Bottom row: "Очистить" button + split send button (`Отправить в | [addressee ▼]`)

`BackHandler` closes sheet on back press.

### HUD wiring

- **Left column, row 3** (`ic_marks_tool`): `selected = markToolActive`, `onClick = toggleMarkTool()` — direct tool shortcut
- **Right column, row 2** (`ic_marks`): `selected = formState.isSheetVisible`, `onClick = toggleGeoMarksSheet()`, `infoBadge = pendingPoints.size` — sheet toggle

**Note**: landscape HUD (`buildHudConfig` → `buildRightColumn`) currently uses default `GeoMarksFormState()` and does not reflect `isSheetVisible`. Sheet itself is hidden in landscape (same as MenuDrawer).

### MapLibreLayer — mark rendering

- Draft marks: `SymbolLayer` with shape bitmap (SDF) tinted by selected color; `LineLayer` connecting points
- Received POINT marks: `SymbolLayer` with shape bitmap tinted by mark color
- Received TRACK marks: `LineLayer` (color from `markColorHex`) + anchor `SymbolLayer`
- Mark tool active: `GestureOptions(isDoubleTapEnabled = false, isQuickZoomEnabled = false)`

### Context menu

Long-tap on draft point within 30m → `GeoMarkContextMenuEvent(pointIndex, screenX, screenY)` via `SharedFlow`. `MainScreen` renders `DropdownMenu` at `Modifier.offset(screenX.dp, screenY.dp)`. "Удалить точку" calls `deletePendingPoint(index)`.

---

## Architecture Notes

- `GeoMarkColor` is Compose-free (pure Kotlin `Int` palette). Presentation wraps with `Color(argb)`.
- `MainViewModel` depends on `GeoMarkPreferencesRepository` interface (domain), not `GeoMarkPrefsDataSource` (data). Koin binds `GeoMarkPreferencesRepositoryImpl`.
- `sendPendingMark()` uses explicit `formState.selectedType` — not inferred from point count.
- Double-tap bypass: `onMapDoubleClick` sends POINT with hardcoded defaults, ignoring sheet form state. Intentional quick-drop gesture.
- Channel routing: `adapter.encode()` returns `channel=0`; `GeoMarkRepositoryImpl` overrides to resolved contour slot.

---

## Resolved Decisions

| Decision | Resolution |
|---|---|
| TTL default | 8 hours (matches `EXPIRE_TTL_SECONDS`); added as 9th option in UI list |
| TrackEndType MVP variants | NONE, ARROW (enum has all 4 for future) |
| Color palette | 16 ARGB Int values indexed 0–15; default color index 4 (Red) |
| Адресат source | Active contours via `ObserveContoursUseCase` + Хранилище (local-only) |
| Preset limit | 10; oldest evicted on overflow |
| DataStore format for presets | Preferences DataStore, JSON-serialised list in single key |
| `ic_close` icon | `Icons.Default.Close` used directly |
| Sheet position | `Alignment.BottomEnd` in `MainScreen` Box; landscape: hidden |
