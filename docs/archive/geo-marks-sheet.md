# Plan: Geo Marks Bottom Sheet

**Date**: 2026-05-18
**Archived**: 2026-05-19
**Status**: Done

## Summary

Replace the floating "Отправить" send panel with a persistent non-modal bottom sheet for mark
creation. The sheet contains: combo preset selector, type + color row, type-specific fields, TTL
(актуальность), name + auto-counter, and адресат + send button. All selections persist between
sessions. Requires domain model extensions, adapter updates, SQLDelight migration, and a new
DataStore persistence layer — not presentation-only.

---

## Source Spec

`C:\Users\LocAdmin\Desktop\Мое\заметки\obsidian\MeshMap\реализация\дизайн\меню создания меток\`
- `Общий интерфейс нижней шторки.md` — full layout spec
- `Создание гео точки.md` — no extra fields
- `Создание трека.md` — end-type dropdown + "точек N из maxPoints"

`C:\Users\LocAdmin\Desktop\Мое\заметки\obsidian\MeshMap\реализация\Передаваемые гео данные\`
- `Трек.md` — законцовки: нет / круг малый с заливкой / круг большой без заливки / стрелка

---

## Sheet Layout

```
┌─────────────────────────────────────────────────┐
│ [✎] [●] {название} {№}/{актуальность}    [▼][X] │  ← ✎ = Edit (стандартный набор);
│                                                  │     ● = вид (форма) с заливкой цвета;
│                                                  │     пример: «ориентир 4/15мин.»;
│                                                  │     [▼] свернуть/развернуть; [X] закрыть
├─────────────────────────────────────────────────┤
│  [▼ Комбинация: {last used}              ]       │  ← preset dropdown (last 10)
├─────────────────────────────────────────────────┤
│  [▼ Тип метки     ]   [▼ ● Цвет  ]             │  ← side by side; Primitive/Polygon disabled
├─────────────────────────────────────────────────┤
│  {type-specific section}                         │
│    Point:   (empty)                              │
│    Track:   [▼ Законцовка]   точек: N / 27      │
│    Primitive / Polygon: (future — not rendered)  │
├─────────────────────────────────────────────────┤
│  [▼ Актуальность: 8 часов              ]        │
├─────────────────────────────────────────────────┤
│  [Название_______________]  [счётчик ▲▼]        │
├─────────────────────────────────────────────────┤
│  [▼ Адресат: {active contour}]   [Отправить]    │
└─────────────────────────────────────────────────┘
```

---

## Scope

**In scope:**
- Sheet composable: full layout per spec
- Mark types in dropdown: POINT, TRACK (functional); POLYGON, PRIMITIVE (disabled items, no send)
- Color: indexed palette; visual swatch in dropdown; packed into `icon` field at encode
- Track end-type: MVP = NONE / ARROW; enum carries full spec (4 values) for future
- TTL / актуальность: 15m / 30m / 1h / 2h / 5h / 12h / 24h / 3d
- Name field + auto-numbering counter (persist, reset on name change, manual override continues)
- Адресат: active contours from `ObserveContoursUseCase`; channel slot resolved via `ChannelSlotResolver`
- Combo preset memory: save after each send, recall by selection; max 10, drop oldest
- All field values persisted between sessions (DataStore)
- `GeoMarkModel`: add `color`, `name`, `trackEndType` fields
- `GeoMarkWaypointAdapter`: pass `color`, `ends`, `name`, `expireTtl` into encode
- SQLDelight migration: add `color`, `name`, `track_end_type` columns
- HUD: `marks` button opens/closes sheet; X button closes + deactivates tool

**Out of scope:**
- POLYGON and PRIMITIVE types (backend support absent)
- Landscape variant (sheet hidden when `isLandscape`, same as `MenuDrawer`)
- Color palette definition (16 slots) — palette constants TBD during Phase 1, define then
- Manual coordinate input

---

## Layers Affected

| Layer | Changes |
|---|---|
| `domain/marker/model/` | `GeoMarkModel` (+color, +name, +trackEndType); new `TrackEndType.kt` |
| `data/marker/adapter/` | `GeoMarkWaypointAdapter` encode/decode — color, ends, name, expire |
| `shared/sqldelight/` | `GeoMark.sq` migration (+3 columns) |
| `data/markprefs/` | **new** DataStore sources for form prefs + presets |
| `domain/marker/repository/` | **new** `GeoMarkPreferencesRepository` interface |
| `presentation/feature/main/` | `MainUiState`, `MainViewModel`, `MainScreen` |
| `presentation/feature/main/osd/` | new `GeoMarksSheet.kt` |
| `presentation/feature/main/osd/models/` | new `GeoMarksSheetUiState.kt` |

---

## Architecture Notes

- **X button** = close sheet + deactivate mark tool (per spec). `markTool` HUD button stays as direct
  toggle power shortcut.
- **`GeoMarksFormState`** — separate StateFlow in ViewModel; form data doesn't belong in MainUiState.
- **Channel routing for адресат**: `encode()` returns `DataPacket(channel=0)`; repo overrides
  `channel` to the resolved slot of the selected contour. Adapter stays transport-focused; routing
  is a repo concern.
- **`ends` byte**: currently hardcoded `0` at adapter line 166 ("reserved for MVP"). This feature
  makes it live — `TrackEndType.ordinal` packed into `ends`.
- **`name` field**: currently hardcoded `""` — now populated from form name+counter.
- **`expire`**: currently hardcoded `EXPIRE_TTL_SECONDS`; now computed from selected TTL.
- **`color`** in `buildIcon()`: currently `color=0`; now from `GeoMarkModel.color`.
- **Presets**: stored as JSON list in DataStore (Proto DataStore or Preferences — TBD in Phase 2).
  Max 10; oldest evicted on overflow. Display name = auto-generated: `"{type} {name} {color}"`.
- **Name counter reset rule**: resets to 1 on text change; continues from current value on manual
  counter edit; auto-increments by 1 after each successful send.
- **Double-tap map gesture** (existing): unchanged — always sends `GeoMarkType.POINT` immediately,
  bypasses sheet form state. This is a distinct "quick drop" gesture.
- **`sendPendingMark()`**: replaces `if (size >= 2) TRACK else POINT` inference with explicit
  `formState.selectedType`.

---

## TrackEndType

```kotlin
enum class TrackEndType(val ends: Byte) {
    NONE(0),
    SMALL_FILLED_CIRCLE(1),
    LARGE_EMPTY_CIRCLE(2),
    ARROW(3),
}
```

MVP UI shows: NONE, ARROW. All 4 values in enum for future.  
Encode: `buf.put(trackEndType.ends)` replaces `buf.put(0)`.  
Decode: `TrackEndType.entries.firstOrNull { it.ends == b } ?: TrackEndType.NONE`.

---

## TTL Options

| Label | Seconds |
|---|---|
| 15 мин | 900 |
| 30 мин | 1800 |
| 1 час | 3600 |
| 2 часа | 7200 |
| 5 часов | 18000 |
| 12 часов | 43200 |
| 24 часа | 86400 |
| 3 суток | 259200 |

Default: 8 hours = 28800 (matches existing `EXPIRE_TTL_SECONDS`). Not in the list above — keep
existing constant as default; add it as 9th option or round to nearest (use 12h as default in UI).

> **Decision needed**: Is 8h default kept (not in list) or replaced with 12h from the list?

---

## Адресат

Source: `ObserveContoursUseCase` → filter `isActive = true`.  
Emergency contour (`isEmergency = true`) included — slot 0 always available.  
Display: contour `name`. Default selection: first active contour (usually DefaultActiveContour).  
Channel resolution at send: `ChannelSlotResolver.hashToSlot(contour.channelHash)` → `Int`.  
Geo send policy: `GeoSendPolicy.observeAllowed()` — already blocks when Emergency active; no
additional check needed in the sheet beyond "Emergency is active = show warning / disable send".

---

## Phase Plan

### Phase 1 — Domain Types

**Files**: `domain/marker/model/`  
**Tasks**:
1. Create `TrackEndType.kt` — enum with 4 values + `ends: Byte` property
2. Add to `GeoMarkModel`: `color: Int = 0`, `name: String = ""`, `trackEndType: TrackEndType = TrackEndType.NONE`
3. Fix all `GeoMarkModel(...)` call sites — add defaults (no behavioural change)
4. Define `GeoMarkColor` object: 16 color constants (ARGB) indexed 0–15. Palette TBD — define here.

### Phase 2 — Adapter + SQLDelight

**Files**: `data/marker/adapter/GeoMarkWaypointAdapter.kt`, `shared/.../GeoMark.sq`  
**Tasks**:
1. `encode()`: use `mark.color`, `mark.trackEndType.ends`, `mark.name`, `ttlSeconds` param instead of hardcodes
   - Add `ttlSeconds: Long` param; replace `EXPIRE_TTL_SECONDS` usage
   - `name = mark.name`
   - `buildIcon(mark.type, color = mark.color, variant = 0)`
   - `buf.put(mark.trackEndType.ends)` replaces `buf.put(0)`
2. `decode()`: extract `color` from icon, `ends` byte from payload, `name` from waypoint.name
   - Populate `GeoMarkModel.color`, `.name`, `.trackEndType`
3. `GeoMark.sq` migration: add columns `color INTEGER NOT NULL DEFAULT 0`,
   `name TEXT NOT NULL DEFAULT ''`, `track_end_type INTEGER NOT NULL DEFAULT 0`
4. Update `GeoMarkRepositoryImpl`: pass color/name/trackEndType through insert/select; channel
   override: `val packet = adapter.encode(...); packet.copy(channel = resolvedSlot)`

### Phase 3 — DataStore Persistence

**Files**: `data/markprefs/` (new package), `domain/marker/repository/`  
**Tasks**:
1. Create `domain/marker/model/GeoMarkFormPreferences.kt`:
   ```kotlin
   data class GeoMarkFormPreferences(
       val selectedType: GeoMarkType = GeoMarkType.POINT,
       val selectedColor: Int = 0,
       val selectedTrackEndType: TrackEndType = TrackEndType.NONE,
       val selectedTtlSeconds: Long = 28800L,
       val markName: String = "",
       val nameCounter: Int = 1,
       val selectedContourId: String = "",  // empty = use first active
   )
   ```
2. Create `domain/marker/model/GeoMarkPreset.kt`:
   ```kotlin
   data class GeoMarkPreset(
       val id: String,
       val displayName: String,  // "{type} {name} {color}"
       val prefs: GeoMarkFormPreferences,
   )
   ```
3. Create `domain/marker/repository/GeoMarkPreferencesRepository.kt` interface:
   - `fun observePreferences(): Flow<GeoMarkFormPreferences>`
   - `fun observePresets(): Flow<List<GeoMarkPreset>>`
   - `suspend fun savePreferences(prefs: GeoMarkFormPreferences)`
   - `suspend fun addPreset(preset: GeoMarkPreset)` — evicts oldest if count > 10
4. Implement with DataStore (Preferences DataStore, JSON-serialized presets list)
5. Koin bindings

### Phase 4 — ViewModel Form State

**Files**: `MainViewModel.kt`  
**Tasks**:
1. Inject `GeoMarkPreferencesRepository`
2. Add `_formState: MutableStateFlow<GeoMarksFormState>` (presentation model):
   ```kotlin
   data class GeoMarksFormState(
       val isSheetVisible: Boolean = false,
       val selectedType: GeoMarkType = GeoMarkType.POINT,
       val selectedColor: Int = 0,
       val selectedTrackEndType: TrackEndType = TrackEndType.NONE,
       val selectedTtlSeconds: Long = 28800L,
       val markName: String = "",
       val nameCounter: Int = 1,
       val selectedContourId: String = "",
       val availableContours: ImmutableList<ContourItem> = persistentListOf(),
       val savedPresets: ImmutableList<GeoMarkPreset> = persistentListOf(),
       val pendingPointCount: Int = 0,  // mirror of mainUiState.pendingMarkPoints.size
   )
   ```
3. Init: load from `observePreferences()`, merge into `_formState`
4. Observe active contours for `availableContours`
5. Add: `toggleGeoMarksSheet()`, `closeGeoMarksSheet()` (also deactivates tool)
6. Add: `setMarkType()`, `setColor()`, `setTrackEndType()`, `setTtl()`, `setMarkName()`,
   `setNameCounter()`, `setAddressee()`, `applyPreset()` — update `_formState`
7. Update `sendPendingMark()`: use `formState.selectedType`, `selectedColor`, etc.;
   resolve contour slot; after send → increment counter, persist prefs, save preset
8. Update `buildHudUiState()`: `marks` button `selected = formState.isSheetVisible`,
   `onClick = { toggleGeoMarksSheet() }`
9. Build `geoMarksSheetUiState`: StateFlow derived from `_formState` + `_uiState`

### Phase 5 — GeoMarksSheet Composable

**Files**: `osd/GeoMarksSheet.kt` (new), `osd/models/GeoMarksSheetUiState.kt` (new)  
**Tasks**:
1. `GeoMarksSheetUiState` data class — all callbacks bundled (MenuDrawerUiState pattern)
2. `GeoMarksSheet` composable:
   - `BackHandler(enabled = visible, onBack = onClose)`
   - `AnimatedVisibility(slideInVertically { it } + fadeIn)`
   - Inner `Column`: fillMaxWidth, wrapContentHeight, top rounded corners 16dp, surface bg,
     navigationBarsPadding, consume touches
3. Header: слева `Icons.Default.Edit`, иконка вида (`GeoMarkShape`) с заливкой `GeoMarkColor`;
   заголовок `{markName} {nameCounter}/{ttlShort}` (пример: `ориентир 4/15мин.`);
   справа свернуть/развернуть + `Icons.Default.Close` (без изменений)
4. Preset section: `ExposedDropdownMenuBox`; empty state = "Нет сохранённых"
5. Type + color row: two `ExposedDropdownMenuBox` side by side; POLYGON/PRIMITIVE = disabled items
6. Type-specific section: `AnimatedContent(targetState = selectedType)`:
   - POINT: `Spacer`
   - TRACK: end-type `ExposedDropdownMenuBox` (NONE, ARROW only visible) + `Text("точек: $n / 27")`
7. TTL row: `ExposedDropdownMenuBox` with 8 options
8. Name + counter row: `OutlinedTextField` + small `OutlinedTextField` for counter (numeric)
9. Bottom row: адресат `ExposedDropdownMenuBox` + `Button("Отправить")`
10. Check `ic_close` in `res/drawable/`; fallback `Icons.Default.Close`

### Phase 6 — MainScreen Wiring

**Files**: `MainScreen.kt`  
**Tasks**:
1. Add `geoMarksSheetUiState: GeoMarksSheetUiState` param
2. Remove floating `AnimatedVisibility` send panel (lines ~156–168)
3. Add `GeoMarksSheet(state = geoMarksSheetUiState)` after HUD layers, before context menu
4. Update call site: collect `geoMarksSheetUiState` StateFlow

### Phase 7 — Simplify & Smoke Test

**Tasks**:
1. `/simplify` on changed files
2. Verify X closes sheet + deactivates tool; `markTool` HUD button still direct toggle
3. Verify sheet stays open during map interaction
4. Verify name counter: increments after send, resets on name change
5. Verify preset save/recall restores all fields
6. Verify TRACK type: end-type dropdown + count display; POINT: no extra fields
7. Verify адресат channel routing (packet.channel matches selected contour slot)

---

## Coordination Map

```
Phase 1: direct coding — domain types + color palette definition
Phase 2: direct coding — adapter + SQLDelight migration
Phase 3: direct coding — DataStore persistence layer
Phase 4: direct coding — ViewModel form state
Phase 5: direct coding — GeoMarksSheet composable  ← check ic_close before starting
Phase 6: direct coding — MainScreen wiring
Phase 7: /simplify → manual smoke test
Phase 8: skill update review (geo-marks doc, ui-designer sheet pattern)
Phase 9: update CLAUDE.md, update .claude/docs/geo-marks.md, archive plan, memory
Phase 10: stage files → propose commit → wait confirmation → git commit
```

---

## Open Questions

| # | Question | Status |
|---|---|---|
| 1 | TrackEndType MVP variants | **Resolved**: NONE, ARROW (enum has all 4) |
| 2 | Color palette (16 colors, 4-bit index) | **Pending**: define in Phase 1 |
| 3 | Адресат source | **Resolved**: active contours via ObserveContoursUseCase |
| 4 | Preset limit | **Resolved**: 10 |
| 5 | TTL default (8h not in list) | **Pending**: keep 8h as default or switch to 12h? |
| 6 | `ic_close` icon exists? | **Pending**: check res/drawable before Phase 5 |
| 7 | DataStore format for presets (Preferences vs Proto) | **Pending**: decide in Phase 3 |

---

## Change Log

- 2026-05-18: created (initial plan, presentation-only scope)
- 2026-05-18: revised — full spec from Obsidian; scope expanded to domain+data+presentation;
  TrackEndType (4 values, MVP=NONE/ARROW), адресат=active contours, presets=10, DataStore layer
- 2026-05-19: header — Edit + shape/color preview + `{name} {№}/{ttlShort}`; collapse/close unchanged
