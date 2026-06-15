# Geo Marks — Points and Tracks

**Date**: 2026-05-25 (track distance, optional name counter, map context menu, send queue)
**Plan archives**: `.claude/archive/geo-marks-plan.md`, `.claude/archive/geo-marks-sheet.md`, `.claude/archive/geo-marks-display-settings.md`
**Related**: `.claude/docs/geo-marks-list.md` (полноэкранный список, фильтры, массовые действия)

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
│   ├── util/
│   │   └── GeoTrackDistance.kt          — last/total segment metres; formatKmRatio for sheet
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
│       ├── GeoMarkRepositoryImpl.kt     — PacketRepository + SQLDelight + adapter; channel override
│       └── GeoMarkSendQueue.kt          — paced WAYPOINT_APP sends (MIN_SEND_INTERVAL_MS)
├── data/markprefs/
│   ├── GeoMarkPrefsDataSource.kt        — DataStore read/write; preset eviction (max 10)
│   └── GeoMarkPreferencesRepositoryImpl.kt — implements GeoMarkPreferencesRepository
└── presentation/feature/main/
    ├── GeoMarksFormState.kt             — internal ViewModel form state (not exposed directly)
    ├── GeoMarkAddresseeDefaults.kt      — default addressee resolver (Basic vs storage)
    ├── MainUiState.kt                   — geoMarks, markToolActive, pendingMarkPoints, selectedGeoMarkId, trackDraftDistanceLabel
    ├── MainViewModel.kt                 — _formState StateFlow, mark tool logic, gesture handlers
    ├── MainScreen.kt                    — GeoMarksSheet; course-up overlay; wires map callbacks
    ├── MarkToolTapDispatcher.kt         — deferred single-tap / double-tap classification
    ├── MarkToolMapTapGestures.kt        — mark-tool taps (non–course-up); pan pass-through
    ├── CourseUpMapGestures.kt           — course-up: Y-zoom + mark taps via dispatcher
    └── osd/
        ├── GeoMarksSheet.kt             — persistent non-modal bottom sheet composable
        ├── GeoMarkMapContextMenu.kt     — Popup menu for existing marks on map (header + actions)
        ├── MapLibreLayer.kt             — gesture callbacks + geo mark rendering layers
        └── models/
            ├── GeoMarksSheetUiState.kt  — all sheet data + callbacks bundled (MenuDrawer pattern)
            ├── GeoMarkContextMenuEvent.kt — sealed: DraftPoint / ExistingMark
            └── GeoMarkAddressee.kt      — contourId + displayName for адресат dropdown

presentation/feature/marks/
├── GeoMarkTitleFormatter.kt             — selectionTitle / authorLabel (map menu + list)
└── GeoMarkCreatedAtFormatter.kt         — created_at labels (map menu header; см. geo-marks-list.md)

shared/src/commonMain/sqldelight/.../data/local/
└── GeoMark.sq                           — geo_mark table; is_visible + color/name/track_end_type/shape
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
    val isVisible: Boolean = true,  // map + list checkbox; SQLDelight is_visible
)
```

### GeoTrackDistance

`domain/marker/util/GeoTrackDistance.kt` — расстояния по черновому треку (haversine через `latLongToMeter`):

| Метод | Назначение |
|---|---|
| `lastSegmentMeters(points)` | метры между двумя последними вершинами; 0 если &lt; 2 точек |
| `totalMeters(points)` | сумма всех сегментов |
| `formatKmRatio(seg, total)` | строка вида `0.123/0.456км` (3 знака после запятой) |

`MainViewModel.withPendingMarkPoints()` обновляет `MainUiState.trackDraftDistanceLabel` при каждом изменении черновика. Шторка TRACK показывает `точек: N / 27` и `trackDistanceLabel` в одной строке.

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

Uses `mark.color`, `mark.name`, `mark.trackEndType.ends`, `mark.expiresAt`. Sets `Waypoint.id` from `mark.waypointId` or `waypointIdFromMarkId(mark.id)` — **never 0** on the wire (Meshtastic treats `id=0` as one shared slot; duplicate sends overwrite each other). Sets `wantAck = false` (broadcast waypoints do not get ACKs; `want_ack=true` blocks the radio queue ~5s per packet). Returns `DataPacket(channel=0)` — channel override is a repo concern.

**Rapid sends**: `sendGeoMark` writes to SQLDelight **immediately** (map + list, delivery state `QUEUED`); `GeoMarkSendQueue` transmits to mesh in the background. Minimum **10.5 s** between radio sends (`MIN_SEND_INTERVAL_MS`) — Meshtastic firmware rate limit for `WAYPOINT_APP` (~10 s). After `handleSend`, `author_node_id` is set → state `SENT`. Faster taps are visible at once; mesh sends are paced.

### `decode(packet, selfIds): GeoMarkModel?`

Extracts `colorIndex = (icon ushr 8) and 0xF`, `endsByte` from payload byte 1, `name` from `waypoint.name`. Populates `GeoMarkModel.color`, `.name`, `.trackEndType`.

**Stable SQLDelight key** (`resolveMarkId`): `wp-{waypoint.id}` when `waypoint.id != 0`; else `pkt-{packet.id}`; else content fingerprint `mt1-…`. No random UUID — repeated ingest of the same packet must not create duplicates.

### `waypointIdFromMarkId(markId: String): Int`

Deterministic non-zero `Waypoint.id` from app mark UUID (xor of UUID bits). Used in `MainViewModel.sendGeoMarkAtPoints` and `encode`.

### Constants

| Constant | Value |
|---|---|
| `MAX_POINTS` | 27 extra points beyond anchor |
| `MAX_PAYLOAD_BYTES` | 145 |
| `EXPIRE_TTL_SECONDS` | 28800 (8h, fallback when `expiresAt` is null) |

---

## Data Flow

### Send

**Кнопка «Отправить» в шторке** или **двойной тап на карте** (см. [Map gestures](#map-gestures-mark-tool)):

```
ViewModel.sendPendingMark()  // или onMapDoubleClick → send для POINT / TRACK
  → sendGeoMarkAtPoints(points, type)
  → builds GeoMarkModel from GeoMarksFormState (type, color, shape, name, ttl, trackEndType)
  → SendGeoMarkUseCase(SendGeoMarkParams(mark, contourId?, localOnly))
  → GeoMarkRepositoryImpl.sendGeoMark()
      → if localOnly: geoMarkQueries.insert() only
      → else: adapter.encode() → packet.channel = resolvedSlot → commandSender.sendData()
              → geoMarkQueries.insert(isSelf=1)
  → increment name counter (if non-null), persist prefs, save preset
```

### Receive

```
IngestReceivedGeoMarksUseCase (launched in ViewModel.init)
  PacketRepository.getWaypoints(): Flow<DataPacket>
  → skip packets with from == ID_LOCAL (own sends already in geo_mark via sendGeoMark)
  → skip ids already in geo_mark or geo_mark_dismissed (user delete must not resurrect)
  → skip when waypoint_id is active or wp-{waypointId} is dismissed (UUID vs wp-* id alias)
  → GeoMarkWaypointAdapter.decode()
  → geoMarkQueries.insertReceived()  // upsert by stable wp-* id

**Delete**: `deleteById` removes row, dismisses both mark id and `wp-{waypointId}` alias, purges mesh history via `deleteWaypoint` / `deleteWaypointByMeshPacketId` (`pkt-*` ids).
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
    shape              INTEGER NOT NULL DEFAULT 0,    -- GeoMarkShape.ordinal
    is_visible         INTEGER NOT NULL DEFAULT 1     -- 1 = on map; ToggleGeoMarkVisibilityUseCase
);
```

Queries: `selectAll`, `selectById`, `selectSelfIds`, `selectAllForChannel`, `insert`, `insertReceived` (`INSERT OR REPLACE` — same `wp-*` id updates coords/color), `setVisible`, `deleteById`, `deleteExpired`.

Миграция `9.sqm`: `ALTER TABLE geo_mark ADD COLUMN is_visible`. Скрытые метки не попадают в `findNearestVisibleMarkId` и не рендерятся в `MapLibreLayer`.

---

## DataStore Persistence

`data/markprefs/` package owns all form state persistence.

`GeoMarkFormPreferences` (domain, `@Serializable`):
- `selectedType: String` (enum name)
- `selectedColor: Int` (palette index)
- `selectedShape: String` (enum name)
- `selectedTrackEndType: Int` (TrackEndType.ends byte value — known inconsistency with String approach for other fields)
- `selectedTtlSeconds: Long`
- `pointMarkName: String` (default `"точка"`)
- `trackMarkName: String` (default `"Путь"`)
- `pointNameCounter: Int?` (default `1`; `null` = номер не используется)
- `trackNameCounter: Int?` (default `1`)
- `selectedContourId: String`

`GeoMarkPrefsDataSource` кодирует `null` счётчик как `NO_NAME_COUNTER = -1` в Preferences DataStore; при чтении `decodeNameCounter` восстанавливает `null`.

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
    val pointMarkName: String,
    val trackMarkName: String,
    val pointNameCounter: Int?,    // persisted; null = поле «№» пустое
    val trackNameCounter: Int?,
    val selectedContourId: String,
    val wasAddresseeExplicitlySelected: Boolean,
    val availableContours: ImmutableList<GeoMarkAddressee>,
    val savedPresets: ImmutableList<GeoMarkPreset>,
)
```

Активное имя/счётчик в UI: по `selectedType` берётся `pointMarkName`/`trackMarkName` и соответствующий counter (`GeoMarksSheetUiState.markName`, `nameCounter: Int?`).

### GeoMarksSheetUiState

Derived StateFlow combining `_uiState + _formState`. All callbacks bundled (MenuDrawerUiState pattern).

### MainViewModel — mark tool

| Method | Behaviour |
|---|---|
| `toggleGeoMarksSheet()` | open/close sheet; opening activates mark tool |
| `closeGeoMarksSheet()` | close sheet + deactivate mark tool + clear pending points |
| `toggleMarkTool()` | direct tool toggle (HUD mark-tool shortcut); does not affect sheet visibility |
| `setMarkType/Color/Shape/TrackEndType/Ttl/MarkName/NameCounter/Addressee()` | update `_formState`, persist to DataStore; **TRACK→POINT**: if draft has >1 vertex, keep only the last as point draft |
| `applyPreset(preset)` | restore all form fields from preset |
| `sendPendingMark()` | send from `pendingMarkPoints`; TRACK requires ≥2 vertices; then clear pending |
| `sendGeoMarkAtPoints(points, type)` | private; shared encode/send path for button and double-tap |
| `onMapClick(lat, lon, screenX, screenY)` | **сначала** `findNearestVisibleMarkId` (30 m, `isVisible`) → `ExistingMarkContextMenuEvent` + `selectedGeoMarkId`; иначе при `markToolActive` — черновик (POINT replace / TRACK append) |
| `onMapDoubleClick(lat, lon)` | только при `markToolActive`: POINT send at tap; TRACK append + `sendPendingMark()` |
| `onMapLongClick(...)` | при `markToolActive`: черновая вершина в 30 m → `DraftPointContextMenuEvent` (non–course-up) |
| `hideGeoMark` / `deleteGeoMark` / `prepareGeoMarkForResend` | из меню существующей метки: скрыть (`toggleVisibility`), удалить, открыть шторку с полями метки для повторной отправки |
| `clearSelectedGeoMark()` | сброс `selectedGeoMarkId` при dismiss меню |
| `clearPendingPoints()` | clear `pendingMarkPoints` from `_uiState` |
| `deletePendingPoint(index)` | remove by index |

### Name counter rules

- Отдельные имена и счётчики по типу: `pointMarkName` / `trackMarkName`, `pointNameCounter` / `trackNameCounter` (`Int?`)
- Поле «№» в шторке можно **очистить** → `null` → в заголовке и в `Waypoint.name` номер не подставляется
- `setMarkName()` сбрасывает счётчик активного типа в `1` (не в `null`)
- `setNameCounter(counter: Int?)` — `null` или значение ≥ 1; персистится в DataStore
- После `sendGeoMarkAtPoints()` счётчик активного типа увеличивается на 1 **только если** был non-null (`?.plus(1)`)
- `buildMarkLabel()`: `"{base} {counter}"` / только base / только counter / `""` в зависимости от пустых полей
- Заголовок шторки: `{name}{optional " N"}/{ttlShort}` — при `nameCounter == null` пробел и номер опускаются

### Addressee logic

`availableContours` = active contours via `ObserveContoursUseCase` + `GeoMarkAddressee("__local__", "Хранилище")`.

Default resolution lives in `GeoMarkAddresseeDefaults.kt` (`resolveDefaultGeoMarkAddresseeId`).

Selection priority:
1. User explicitly chose addressee in session (`setAddressee`) and id still in list → keep it
2. Dynamic default via `resolveDefaultGeoMarkAddresseeId`:
   - **Connected** + at least one active non-Emergency contour → **Basic** (`DefaultActiveContour`) if active, else first active non-Emergency contour
   - **Disconnected** or no eligible contours → **Хранилище** (`__local__`, local only)
3. DataStore `geomark_contour_id`: only a real contour UUID restores explicit choice on startup; `__local__` or empty does **not** block auto-switch when node connects

Emergency (`DefaultContour`) is never used as dynamic default even if `isActive`.

**Реактивное переключение**: `combine(observeLogicalChannels, connectionStatus.distinctUntilChanged())` в `MainViewModel.init` — при подключении/отключении ноды и смене активных контуров `selectedContourId` пересчитывается без перезапуска (если пользователь не зафиксировал явный выбор и контур ещё в списке).

### GeoMarksSheet composable

Non-modal bottom sheet. `AnimatedVisibility(slideInVertically + fadeIn)` at `Alignment.BottomEnd` in `MainScreen`.

**Header**: Edit icon | ShapeIcon with fill color | `{name}{optional counter}/{ttlShort}` | collapse | close

**Body** (hidden when collapsed):
- Type dropdown (POINT, TRACK; POLYGON/PRIMITIVE disabled items)
- **Вид** dropdown + Color dropdown (side by side with type): "Вид" switches content by type — POINT shows `ShapeDropdown` (CIRCLE/SQUARE/TRIANGLE via `ShapeIcon`), TRACK shows `TrackEndTypeDropdown` (NONE/ARROW via `TrackEndTypeIcon`)
- Type-specific section: TRACK — `TrackSection`: `точек: N / 27` и `trackDraftDistanceLabel` (`0.123/0.456км`); POINT — пусто
- Name field + counter field + TTL dropdown (9 options: 15m … 3 days)
- Bottom row: "Очистить" button + split send button (`Отправить в | [addressee ▼]`)

`TrackEndTypeIcon` — Canvas composable (аналог `ShapeIcon`): рисует горизонтальную линию с законцовкой по типу. NONE: линия. ARROW: линия + стрелочная голова (вершина справа, крылья уходят назад-влево). SMALL_FILLED_CIRCLE: линия + заполненный круг. LARGE_EMPTY_CIRCLE: линия + пустой круг (stroke).

`BackHandler` closes sheet on back press.

### HUD wiring

- **Left column, row 3** (`ic_marks_tool`): `selected = markToolActive`, `onClick = toggleMarkTool()` — direct tool shortcut
- **Right column, row 2** (`ic_marks`): `selected = formState.isSheetVisible`, `onClick = toggleGeoMarksSheet()`, `infoBadge = pendingPoints.size` — sheet toggle

**Note**: landscape HUD (`buildHudConfig` → `buildRightColumn`) currently uses default `GeoMarksFormState()` and does not reflect `isSheetVisible`. Sheet itself is hidden in landscape (same as MenuDrawer).

### MapLibreLayer — mark rendering

- Draft marks: `SymbolLayer` (`geo-draft-points`) with shape bitmap (SDF) tinted by selected color; `LineLayer` connecting points
- Received POINT marks: `SymbolLayer` (`geo-received-points`) with shape bitmap tinted by mark color
- Received POINT name labels: separate `SymbolLayer` (`geo-received-point-labels`), same source as points, added only when `showGeoMarkNames = true`. Two layers necessary — single layer with conditional text params causes MapLibre layer ID conflict on recomposition.
- Received TRACK marks: `LineLayer` (color from `markColorHex`) + anchor `SymbolLayer`
- `selectedGeoMarkId`: подсветка выбранной метки (толще линия трека / увеличенный anchor point)
- Рендер только `mark.isVisible == true` (черновик всегда виден)
- `geoMarkSizeLevel` (1–10) controls `iconSize` of point/draft layers: `(36 + (level-1) * 6) / 64f` → range [0.5625, 1.406]. Track anchor scaled at 70% of point size.
- `buildReceivedPointsGeoJson` includes `"name"` property; empty names render no label (`textOptional = true`).
- Mark tool active: `GestureOptions(isDoubleTapEnabled = false, isQuickZoomEnabled = false)` — native double-tap zoom off; double-tap handled in Compose
- `markToolActive && !isCourseUpActive`: modifier `markToolMapTapGestures` on `MaplibreMap`
- `markToolActive`: `onMapClick` / `onMapLongClick` of MapLibre **not** forwarded (Compose layer owns taps)

### Map gestures (mark tool)

Полная спецификация course-up (Y-zoom, без long-tap): `.claude/docs/map-orientation.md` → **Course-up + добавление геометок**.

#### Общий слой: `MarkToolTapDispatcher`

Используется в `MarkToolMapTapGestures` и `CourseUpMapGestures` (при `markToolActive`).

```
Первый release (короткий тап)
  → запуск coroutine delay(ViewConfiguration.getDoubleTapTimeout())
  → по истечении: onSingleTap → ViewModel.onMapClick

Второй release внутри doubleTapTimeout
  → отмена отложенного single
  → onDoubleTap → ViewModel.onMapDoubleClick

Pan / zoom / long-press в оверлее
  → dispatcher.reset() — сброс окна double-tap
```

ViewModel **не** дублирует логику double-tap (нет второго `onMapClick` → send).

#### Одинарный тап: приоритет существующей метки

`onMapClick` **всегда** сначала ищет ближайшую **видимую** метку в радиусе 30 m (`findNearestVisibleMarkId` — любая вершина point/track). Если найдена — меню существующей метки, **без** требования `markToolActive`. Иначе — логика черновика ниже (только при `markToolActive`).

#### Режим без course-up (`MarkToolMapTapGestures`)

| Жест | Поведение |
|---|---|
| Одинарный тап | `onMapClick` после `doubleTapTimeout` (метка или черновик) |
| Двойной тап | При `markToolActive` — см. таблицу ниже |
| Перетаскивание (pan) | События **не consume** — MapLibre scroll |
| Тап (короткий) | `change.consume()` на UP — MapLibre не дублирует клик |
| Long tap | При `markToolActive`: `onMapLongClick` → меню черновой точки |

Координаты тапа: `projection.positionFromScreenLocation` в позиции **DOWN** (как в course-up).

#### Режим course-up (`CourseUpMapGestures`)

| Жест | Поведение |
|---|---|
| Одинарный тап | `MarkToolTapDispatcher` → `onMapClick` (метка или черновик) |
| Двойной тап | При `markToolActive` → `onMapDoubleClick` |
| Движение по Y > touchSlop | Zoom камеры (не consume до порога; consume при zoom) |
| 2+ пальца | Точку не ставить; pinch — MapLibre |
| Long tap | Черновик не обрабатывается |

При `markToolActive && isCourseUpActive`: `isScrollEnabled = false` в MapLibre — pan только через Y-zoom оверлея.

#### Действия по типу метки (double-tap, только `markToolActive`)

| `selectedType` | Одинарный тап (нет метки под курсором) | Двойной тап |
|---|---|---|
| **POINT** | Одна черновая вершина (заменяет предыдущий черновик POINT) | Отправка **одной** точки в координатах тапа; параметры из шторки; черновик очищается |
| **TRACK** | Вершина **добавляется** к полилинии черновика | Вершина в точке тапа **добавляется**, затем `sendPendingMark()`; нужно **≥2** вершины суммарно; иначе отправки нет |

Отправка с кнопки «Отправить» эквивалентна `sendPendingMark()` без добавления вершины в месте тапа.

#### Поток данных (жесты)

```
MainScreen
  ├─ MapLibreLayer(markToolMapTapGestures?)     // север вверх + markTool
  └─ Box(courseUpMapGestures?)                  // course-up overlay

MarkToolMapTapGestures / CourseUpMapGestures
  → MarkToolTapDispatcher
  → onMapClick / onMapDoubleClick

NavGraph → MainViewModel::onMapClick / onMapDoubleClick
```

### Context menu

`GeoMarkContextMenuEvent` (sealed) эмитится через `MainViewModel.contextMenuEvent` (`SharedFlow`). `MainScreen` подписывается и ренерит UI по типу события.

#### `DraftPointContextMenuEvent` (long-tap, `markToolActive`)

Long-tap на черновой вершине в 30 m (non–course-up) → `DropdownMenu` в `Modifier.offset(screenX.dp, screenY.dp)`:

- **Удалить точку** → `deletePendingPoint(index)`

#### `ExistingMarkContextMenuEvent` (single-tap на видимой метке)

Single-tap в 30 m от любой вершины видимой метки → `GeoMarkMapContextMenu` (`Popup`):

- **Заголовок**: иконка типа, `{name} от {author}`, справа `GeoMarkCreatedAtFormatter` по `mark.createdAt` (формат как в списке — см. `.claude/docs/geo-marks-list.md`)
- **Скрыть** → `hideGeoMark` (`ToggleGeoMarkVisibilityUseCase`, `is_visible = 0`)
- **Удалить** → `deleteGeoMark` (`DeleteGeoMarksUseCase`)
- **Отправить** → `prepareGeoMarkForResend` (открывает шторку, подставляет поля метки для повторной отправки)

`selectedGeoMarkId` в `MainUiState` выставляется при открытии меню; сбрасывается в `clearSelectedGeoMark()` при dismiss.

---

## Architecture Notes

- `GeoMarkColor` is Compose-free (pure Kotlin `Int` palette). Presentation wraps with `Color(argb)`.
- `MainViewModel` depends on `GeoMarkPreferencesRepository` interface (domain), not `GeoMarkPrefsDataSource` (data). Koin binds `GeoMarkPreferencesRepositoryImpl`.
- `sendPendingMark()` uses explicit `formState.selectedType` — not inferred from point count.
- Map taps при `markToolActive`: черновик/отправка — только Compose (`MarkToolMapTapGestures` / `CourseUpMapGestures`); MapLibre `onMapClick` отключён. Тап по **существующей** метке обрабатывается тем же `onMapClick` и работает и без mark tool.
- `GeoTrackDistance` в domain/util — без зависимости от Compose; форматирование для UI в presentation.
- `MarkToolTapDispatcher` откладывает single-tap на `ViewConfiguration.getDoubleTapTimeout()`; double-tap не вызывает промежуточный `onMapClick` для второго нажатия.
- Channel routing: `adapter.encode()` returns `channel=0`; `GeoMarkRepositoryImpl` overrides to resolved contour slot.

---

## Resolved Decisions

| Decision | Resolution |
|---|---|
| TTL default | 8 hours (matches `EXPIRE_TTL_SECONDS`); added as 9th option in UI list |
| TrackEndType MVP variants | NONE, ARROW (enum has all 4 for future) |
| Color palette | 16 ARGB Int values indexed 0–15; default color index 4 (Red) |
| Адресат source | Active contours via `ObserveContoursUseCase` + Хранилище (local-only); default: Basic when connected, storage when not |
| Preset limit | 10; oldest evicted on overflow |
| DataStore format for presets | Preferences DataStore, JSON-serialised list in single key |
| `ic_close` icon | `Icons.Default.Close` used directly |
| Sheet position | `Alignment.BottomEnd` in `MainScreen` Box; landscape: hidden |
| Course-up + mark tool gestures | `CourseUpMapGestures.kt` + `MarkToolTapDispatcher`; см. `map-orientation.md` |
| Mark tool без course-up | `MarkToolMapTapGestures.kt` на `MaplibreMap`; pan не блокируется |
| Double-tap POINT | Отправка в точке второго тапа |
| Double-tap TRACK | Вершина в точке тапа + `sendPendingMark()` |
| Long tap при course-up + метки | Не обрабатывать |
| 2+ пальца при course-up + метки | Только zoom, точку не ставить |
| Name labels layer separation | Отдельный слой `geo-received-point-labels` вместо if/else на одном id — MapLibre не обновляет свойства слоя при смене ветки Compose с одинаковым id |
| Geo mark icon size formula | `(36 + (level-1)*6) / 64f`; диапазон 36–90dp в настройках; 64 = размер bitmap в px |
| Name counter optional | `Int?` в форме и DataStore; пустое поле «№» → без номера в имени и заголовке |
| Track draft distance | `GeoTrackDistance.formatKmRatio` в шторке TRACK при наборе вершин |
| Map tap on existing mark | Single-tap 30 m → `GeoMarkMapContextMenu`; не требует mark tool |
| Addressee on connect | `combine(channels, connectionStatus)` пересчитывает адресат при появлении ноды |
| Waypoint send queue | SQLDelight сразу + `GeoMarkSendQueue` с интервалом 10.5 s; уникальный `Waypoint.id` |
