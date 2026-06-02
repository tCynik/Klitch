# Plan: Track Recording

**Date**: 2026-06-01  
**Status**: Draft  
**Branch**: track_recording

---

## Summary

Users can start and stop automatic GPS track recording directly from the map HUD. Recording runs in the `GpsService` scope (survives backgrounding, including app minimization). A collapsible bottom sheet (`Alignment.BottomStart`) shows live status when collapsed and preset/settings when expanded. **Closing the sheet does not stop recording** — the HUD button remains `selected` as the only indicator. The sheet is mutually exclusive with the GeoMarks sheet (opening one closes the other). Recording uses preset profiles (walking, bicycle, moto, car, airplane, custom) with configurable time interval (or "-") and minimum distance; a point is recorded when either condition is met first. Completed tracks appear in the Marks list screen under a new "Треки" filter button.

---

## Scope

**In scope:**
- HUD button `trackRecord` in left column, bottom group (after `gps`); `selected = isRecording`
- `TrackRecordingSheet` — collapsible; **closing sheet does not stop recording**
- Sheet mutual exclusion with `GeoMarksSheet` (open one → the other closes)
- Recording engine in `GpsService` scope (survives backgrounding)
- Recording trigger: time OR distance (whichever comes first)
- Preset profiles: 6 presets (Пешком, Велосипед, Мото, Автомобиль, Самолёт, Кастом)
- Customising any preset parameter → auto-switch to Кастом
- SQLDelight tables: `recorded_track` + `recorded_track_point` (migration `11.sqm`)
- Settings: name, name counter, preset, interval, min distance, color — persisted to DataStore
- Marks list integration: `recordedTrackItems` + "Треки" toggle in AppBar
- `RecordedTrackListItem` composable in `presentation/feature/marks/`

**Out of scope (planned extensions):**
- Map rendering of finished recorded tracks on MapLibre
- Export (GPX, KML)
- Track editing / trimming
- Sharing via mesh (sending as GeoMarkType.TRACK waypoint)
- Pause recording
- Adjusting GPS polling frequency per preset (currently fixed at 5 s in `GpsService`)

---

## Architecture Notes

### Recording Preset Model

```kotlin
enum class TrackRecordingPreset { WALKING, BICYCLE, MOTO, CAR, AIRPLANE, CUSTOM }

data class TrackRecordingSettings(
    val preset: TrackRecordingPreset = TrackRecordingPreset.WALKING,
    val intervalSeconds: Int? = 10,      // null = "-" → only distance triggers recording
    val minDistanceMeters: Int = 5,      // 0 = no distance constraint
    val name: String = "Трек",
    val nameCounter: Int? = 1,
    val color: Int = 0,
)
```

**Default preset values:**

| Preset | intervalSeconds | minDistanceMeters |
|--------|----------------|-------------------|
| WALKING | 10 | 5 |
| BICYCLE | 5 | 15 |
| MOTO | 5 | 30 |
| CAR | 5 | 50 |
| AIRPLANE | 10 | 200 |
| CUSTOM | user | user |

**Interval options in UI**: `null` ("-") / 5 / 10 / 30 / 60 / 300 seconds.  
**Min distance options in UI**: 0 ("-") / 5 / 10 / 15 / 30 / 50 / 100 / 200 / 500 metres.

**Custom preset auto-selection**: when user manually changes any field (interval, distance, color, name) while a non-Custom preset is active → `preset` field switches to `CUSTOM` automatically. Selecting a named preset restores its default interval/distance.

**Recording trigger** (per incoming GPS location):
```
val timeSinceLast = (now - lastPointTimestampMs) / 1000
val distFromLast  = haversine(lastPoint, newPoint)

val timeTriggered = settings.intervalSeconds != null && timeSinceLast >= settings.intervalSeconds
val distTriggered = settings.minDistanceMeters > 0 && distFromLast >= settings.minDistanceMeters

if (timeTriggered || distTriggered || isFirstPoint) → record point
```

**GPS polling constraint**: `GpsService` emits locations at 5 s intervals. For presets with `intervalSeconds < 5`, the effective minimum interval is still 5 s. This is a known limitation documented in the feature doc.

### New domain package: `domain/track/`

```
domain/track/
├── model/
│   ├── RecordedTrack.kt           — id, name, startedAt, finishedAt?, totalDistanceMeters, color, isVisible
│   ├── TrackPoint.kt              — trackId, timestampMs, lat, lon, accuracy
│   ├── TrackRecordingSettings.kt  — see above
│   ├── TrackRecordingPreset.kt    — enum
│   └── TrackRecordingState.kt     — sealed: Idle | Recording(trackId, name, startedAtSeconds, distanceMeters, pointCount)
├── repository/
│   ├── RecordedTrackRepository.kt     — observeTracks / setVisible / deleteById
│   └── TrackRecordingRepository.kt    — observeState / start(settings) / stop / addPoint
└── usecase/
    ├── ObserveRecordedTracksUseCase.kt
    ├── StartTrackRecordingUseCase.kt
    ├── StopTrackRecordingUseCase.kt
    ├── ToggleRecordedTrackVisibilityUseCase.kt
    ├── DeleteRecordedTracksUseCase.kt
    └── ObserveTrackRecordingStateUseCase.kt
```

### New data package: `data/track/`

```
data/track/
├── repository/
│   ├── RecordedTrackRepositoryImpl.kt      — SQLDelight queries
│   └── TrackRecordingRepositoryImpl.kt     — StateFlow<TrackRecordingState>; start/stop/addPoint
├── datasource/
│   └── TrackSettingsDataSource.kt          — DataStore read/write for TrackRecordingSettings
└── TrackModule.kt                          — Koin bindings
```

### SQLDelight schema — migration `11.sqm`

```sql
CREATE TABLE recorded_track (
    id               TEXT    NOT NULL PRIMARY KEY,
    name             TEXT    NOT NULL DEFAULT '',
    started_at       INTEGER NOT NULL,
    finished_at      INTEGER,
    total_distance   REAL    NOT NULL DEFAULT 0.0,
    color            INTEGER NOT NULL DEFAULT 0,
    is_visible       INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE recorded_track_point (
    track_id    TEXT    NOT NULL,
    timestamp   INTEGER NOT NULL,       -- unix milliseconds
    lat         REAL    NOT NULL,
    lon         REAL    NOT NULL,
    accuracy    REAL    NOT NULL DEFAULT 0.0,
    PRIMARY KEY (track_id, timestamp)
);
```

`RecordedTrack.sq` queries: `insert`, `updateFinished`, `setVisible`, `deleteById`, `selectAll ORDER BY started_at DESC`, `selectById`.  
`RecordedTrackPoint.sq` queries: `insertPoint`, `selectByTrackId`, `deleteByTrackId`.

### GpsService extension

`GpsService` injects `TrackRecordingRepository`. In `onStartCommand`, launches a coroutine on a manually managed `CoroutineScope(SupervisorJob() + Dispatchers.Default)` stored as a field (avoids `LifecycleService` dependency). The coroutine:

1. Collects `TrackRecordingRepository.observeState()`.
2. On `Recording` state: launches inner collection job — collects `GpsRepository.locationFlow`, applies time+distance trigger logic (see Recording trigger above), calls `trackRecordingRepository.addPoint(point)`.
3. On `Idle` state: cancels inner collection job.
4. `onDestroy` calls `serviceScope.cancel()` → all recording coroutines are cancelled.

`TrackRecordingRepositoryImpl` owns `MutableStateFlow<TrackRecordingState>`.  
`start(settings)`: inserts `recorded_track` row; transitions state to `Recording`.  
`addPoint(point)`: inserts `recorded_track_point` row; updates `distanceMeters` + `pointCount` in in-memory state.  
`stop()`: computes total distance from all points, calls `updateFinished`, transitions state to `Idle`.  
Distance reuses `GeoTrackDistance.latLongToMeter` (accessible via shared domain util or copied to `domain/track/util/`).

### Presentation — MainViewModel

Follows the `GeoMarksFormState` pattern:

- `_trackFormState: MutableStateFlow<TrackRecordingFormState>` (internal; loaded from DataStore on init)
- `trackRecordingSheetUiState: StateFlow<TrackRecordingSheetUiState>` — derived via `combine(_trackFormState, ObserveTrackRecordingStateUseCase(), tickerFlow)`
- `tickerFlow` = 1-second ticker, active only when `TrackRecordingState == Recording` (to update elapsed display)

**Sheet mutual exclusion** (in `MainViewModel`):
- `toggleTrackRecordingSheet()` → if opening: call **`closeGeoMarksSheet()`** (full close: deactivates mark tool + clears pending points — identical to pressing the close button on the marks sheet)
- `toggleGeoMarksSheet()` → if opening: call `closeTrackRecordingSheetVisibility()` (visibility only; does NOT stop recording)

**Closing sheet does NOT stop recording**:
- `closeTrackRecordingSheetVisibility()` sets `_trackFormState.isSheetVisible = false`; recording state is unchanged
- `stopTrackRecording()` is a separate ViewModel method, called only from the sheet's "Стоп" button

**HUD button**: `trackRecord: HudRowConfig` in `HudUiState`.  
`selected = recordingState is TrackRecordingState.Recording` — always reflects recording status, regardless of sheet visibility.

**ViewModel methods**: `toggleTrackRecordingSheet()`, `closeTrackRecordingSheetVisibility()`, `startTrackRecording()`, `stopTrackRecording()`, `setTrackPreset(preset)`, `setTrackInterval(seconds?)`, `setTrackMinDistance(meters)`, `setTrackName()`, `setTrackNameCounter()`, `setTrackColor()`.

Preset logic in ViewModel: `setTrackPreset` restores default interval+distance for the preset; any call to `setTrackInterval` / `setTrackMinDistance` triggers `setTrackPreset(CUSTOM)` if current preset != CUSTOM.

### TrackRecordingFormState

```kotlin
data class TrackRecordingFormState(
    val isSheetVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val settings: TrackRecordingSettings = TrackRecordingSettings(),
)
```

### TrackRecordingSheetUiState

```kotlin
data class TrackRecordingSheetUiState(
    val isVisible: Boolean = false,
    val isCollapsed: Boolean = false,
    val settings: TrackRecordingSettings = TrackRecordingSettings(),
    val recordingState: TrackRecordingState = TrackRecordingState.Idle,
    val durationSeconds: Long = 0L,
    // Callbacks
    val onClose: () -> Unit = {},
    val onToggleCollapsed: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onPresetSelected: (TrackRecordingPreset) -> Unit = {},
    val onIntervalSelected: (Int?) -> Unit = {},
    val onMinDistanceSelected: (Int) -> Unit = {},
    val onNameChanged: (String) -> Unit = {},
    val onNameCounterChanged: (Int?) -> Unit = {},
    val onColorSelected: (Int) -> Unit = {},
)
```

### Sheet UI

**Collapsed** (both states):
```
[● REC / ○] 00:12:34  1.24 км  [▼]
```

**Expanded — IDLE:**
- Preset row: 6 chips (Пешком / Велосипед / Мото / Авто / Самолёт / Кастом)
- Settings row: interval dropdown + min distance dropdown (side by side)
- Name field + counter field + color picker
- [Старт] button

**Expanded — RECORDING:**
- Header: `● REC {name} {counter}`
- Stats: Время: `00:12:34` | Расстояние: `1.24 км` | Точек: `234`
- Current preset chip (read-only) + interval + distance (read-only)
- [Стоп] button

`BackHandler` calls `onClose` (closes sheet, does NOT stop recording).

### HUD wiring

`HudUiState.trackRecord: HudRowConfig`:
- `selected = recordingState is Recording`
- `onClick = toggleTrackRecordingSheet()`
- No badge (selected state alone is the recording indicator)

`HudPortraitControlsLayer`: render `state.trackRecord` after `state.gps` in left bottom group.

### GeoMarksList integration

`GeoMarksListUiState` extended:
- `recordedTrackItems: ImmutableList<RecordedTrackListItemUiModel>` (separate list, not merged with `items`)
- `showRecordedTracks: Boolean`
- `hasRecordedTracks: Boolean` (for filter button INACTIVE state)

`GeoMarksListViewModel` gains `ObserveRecordedTracksUseCase`, `ToggleRecordedTrackVisibilityUseCase`, `DeleteRecordedTracksUseCase`.

AppBar: new `IconButton` with `ic_track_record` icon, follows `GeoMarkDeliveryFilterButton` pattern (INACTIVE / SELECTED / UNSELECTED).

`RecordedTrackListItemUiModel`:
```kotlin
data class RecordedTrackListItemUiModel(
    val id: String,
    val name: String,
    val color: Int,
    val isVisible: Boolean,
    val startedAtLabel: String,     // GeoMarkCreatedAtFormatter format
    val durationLabel: String,      // "1ч 23м" / "45 мин"
    val distanceLabel: String,      // "1.24 км"
    val pointCount: Int,
)
```

`RecordedTrackListItem` row: checkbox (visibility) | route icon | name | badge «запись» | subtitle: `{startedAtLabel} • {durationLabel} • {distanceLabel}` | ⋮ menu: Удалить.

Recorded tracks appear **above** geo mark items in `LazyColumn` when filter is active.

---

## Phase Plan

### Phase 1 — Architecture Design

**Goal**: Approved architecture — domain model, repository contracts, preset model, ViewModel state shape, GpsService extension contract.

**Key decisions to confirm:**
- `TrackRecordingSettings` model and preset defaults (above; confirm values are sensible)
- `GpsService` manual `CoroutineScope` approach (above)
- `TrackRecordingRepository` owns state + DB; `GpsService` observes state and drives collection coroutine
- `GeoTrackDistance.latLongToMeter` reuse vs copy to `domain/track/util/`
- All Koin bindings: `TrackModule`, use cases, repos, `TrackSettingsDataSource`
- `.sq` file split: `RecordedTrack.sq` + `RecordedTrackPoint.sq`

**Skill**: `/architect feature: track recording — recording preset model, GpsService CoroutineScope pattern, ViewModel sheet mutual exclusion, SQLDelight schema`

**Output**: architecture plan; all decisions confirmed before Phase 3.

> **Token checkpoint**: run `/compact` after Phase 1 before Phase 2.

---

### Phase 2 — UI / Icon Design

**Goal**: Approved icon and sheet layout.

**Tasks**:
- New icon `ic_track_record` (HUD button): invoke `/icon-designer`
- REC status indicator for collapsed sheet header (dot + "REC" label vs animated dot)
- Confirm preset chip row layout (6 chips — compact enough in sheet width?)
- Confirm interval + min distance dropdowns side by side
- Confirm "Треки" filter button reuses `GeoMarkDeliveryFilterButton` with `ic_track_record` or a separate route icon

**Skill**: `/icon-designer create: ic_track_record — HUD track recording button`

**Output**: `ic_track_record.xml`, confirmed sheet layout.

---

### Phase 3 — Implementation

**Goal**: Working code. Order: domain → data → DI → presentation.

**Tasks** (ordered):

1. `TrackRecordingPreset`, `TrackRecordingSettings`, `RecordedTrack`, `TrackPoint`, `TrackRecordingState` in `domain/track/model/`
2. `RecordedTrackRepository`, `TrackRecordingRepository` interfaces in `domain/track/repository/`
3. 6 use cases in `domain/track/usecase/`
4. Migration `11.sqm`, `RecordedTrack.sq`, `RecordedTrackPoint.sq`
5. `RecordedTrackRepositoryImpl`, `TrackRecordingRepositoryImpl`, `TrackSettingsDataSource` in `data/track/`
6. `TrackModule` Koin bindings; register in app module
7. `GpsService` extension: manual `CoroutineScope` field; inject `TrackRecordingRepository`; inner collection coroutine with time+distance trigger
8. `TrackRecordingFormState`; load settings from DataStore in `MainViewModel.init`; all ViewModel methods
9. Derived `trackRecordingSheetUiState` + ticker in `MainViewModel`
10. **Sheet mutual exclusion** in `MainViewModel` (`toggleTrackRecordingSheet`, `toggleGeoMarksSheet` — both sides)
11. `HudUiState.trackRecord: HudRowConfig`; wire `selected = isRecording` in ViewModel; render in `HudPortraitControlsLayer`
12. `TrackRecordingSheetUiState` data class + `TrackRecordingSheet` composable (`osd/`)
13. Wire `TrackRecordingSheet` in `MainScreen` at `Alignment.BottomStart`; `BackHandler` precedence
14. `GeoMarksListViewModel` extended; `RecordedTrackListItemUiModel` + formatter helpers
15. `RecordedTrackListItem` composable; `GeoMarksListScreen` AppBar + `LazyColumn` extension

After all tasks: run `/simplify` on changed files.

**Logger checklist**: `TrackRecordingRepositoryImpl`, `RecordedTrackRepositoryImpl`, `TrackSettingsDataSource` each take `logger: Logger`, tag `"Track"`.

---

### Phase 3c — Service Robustness (Non-User-Initiated Termination)

**Goal**: Корректное завершение записи во всех сценариях, когда пользователь не нажал «Стоп».

#### Сценарии и обработка

| Сценарий | `onDestroy`? | Обработка |
|---|---|---|
| Явный выход через диалог | ✓ (после `stopService`) | Уже корректно ✓ |
| Система убила сервис (OOM) | ✓ | `GpsService.onDestroy()` → `finishIfRecording()` |
| Force-stop / kill процесса | ✗ | Orphan recovery при старте |
| Перезагрузка устройства | ✗ | Orphan recovery при старте |

#### Задачи

1. **`TrackRecordingRepository`** — добавить метод `suspend fun finishIfRecording()` в интерфейс.

2. **`TrackRecordingRepositoryImpl.finishIfRecording()`**:
   ```kotlin
   override suspend fun finishIfRecording() {
       val state = _state.value as? TrackRecordingState.Recording ?: return
       db.updateFinished(
           id           = state.trackId,
           finishedAt   = System.currentTimeMillis() / 1000,
           totalDistance = state.distanceMeters,
       )
       _state.value = TrackRecordingState.Idle
   }
   ```
   Только один `UPDATE` — завершается за ~1–2 мс, ANR не угрожает.

3. **`GpsService.onDestroy()`**:
   ```kotlin
   override fun onDestroy() {
       runBlocking { trackRecordingRepository.finishIfRecording() }
       serviceScope.cancel()
       super.onDestroy()
   }
   ```

4. **SQL** — добавить запрос `selectAllOpenTracks` в `RecordedTrack.sq`:
   ```sql
   selectAllOpenTracks:
   SELECT * FROM recorded_track WHERE finished_at IS NULL;
   ```

5. **Orphan recovery** — вызывается в `TrackRecordingRepositoryImpl.init` (приватный suspend-метод, запускается через `CoroutineScope(SupervisorJob()).launch`):

   **Константа**: `ORPHAN_RECOVERY_DEADLINE_MS = 12 * 60 * 60 * 1000L` (12 часов).

   **Логика**:
   ```
   lastActivityMs = MAX(point.timestamp) FOR track
                    ?: track.startedAt * 1000   // если точек ещё не было

   if (now - lastActivityMs ≤ 12 ч):
       → auto-save: updateFinished(id, lastActivityMs/1000, calculatedDist)
       → logger.i("Track", "Recovered orphaned track $id")
   else:
       → discard: deleteByTrackId(points) + deleteById(track)
       → logger.w("Track", "Discarded stale orphaned track $id")
   ```

   **Обоснование дедлайна**: 12 часов — достаточно, чтобы сохранить трек при перезагрузке устройства в походе и возврате на следующее утро; достаточно коротко, чтобы «забытая» запись недельной давности не появилась в списке.

6. Вспомогательный метод `calculateTotalDistanceFromPoints(points: List<TrackPoint>): Double` — переиспользует `GeoTrackDistance.latLongToMeter`; размещается в `TrackRecordingRepositoryImpl` (private).

**Logger checklist**: `finishIfRecording` и orphan recovery логируют через тег `"Track"`.

---

### Phase 3d — Background Recording Reminder Notification

**Goal**: Каждые 2 часа активной фоновой записи пользователь получает push-напоминание с актуальной статистикой.

**Условие показа**: запись активна (`TrackRecordingState.Recording`) **и** приложение не на переднем плане.

#### Задачи

1. **Константа** в `GpsService`:
   ```kotlin
   private const val RECORDING_REMINDER_INTERVAL_MS = 2 * 60 * 60 * 1000L
   ```

2. **Канал уведомлений** — создать отдельный канал `"track_reminder"` (importance `IMPORTANCE_DEFAULT`) при инициализации `GpsService`. Отдельный канал нужен, чтобы пользователь мог отключить напоминания, не трогая постоянное foreground-уведомление GPS.

3. **Periodic reminder coroutine** в `GpsService`:
   - Запускается/перезапускается при переходе в `Recording`; отменяется при `Idle`.
   - Внутри: `delay(RECORDING_REMINDER_INTERVAL_MS)`, затем — проверка `ProcessLifecycleOwner.get().lifecycle.currentState` (если `RESUMED` — пропустить, приложение активно; иначе — отправить уведомление).
   - Цикл продолжается до отмены job.

4. **Текст уведомления**:
   - Заголовок: `"Запись трека активна"`
   - Текст: `"${rs.name} • ${formatDuration(accumulatedSeconds)} • ${formatDistance(distanceMeters)}"`
   - `contentIntent`: PendingIntent открывает `MainActivity` (app to front).
   - `autoCancel = true`.

5. **Отмена**: при `Idle` отозвать уведомление через `NotificationManagerCompat.cancel(REMINDER_NOTIFICATION_ID)`.

6. **Reminder job** хранится как поле `GpsService` (аналогично inner collection job), управляется из coroutine, которая наблюдает за `TrackRecordingRepository.observeState()`.

**ProcessLifecycleOwner** требует зависимости `androidx.lifecycle:lifecycle-process` (скорее всего уже есть в проекте).

---

### Phase 4 — Testing

**Tasks**:
- `TrackRecordingTriggerTest` — time+distance trigger logic (both conditions, either alone, first-point)
- `RecordedTrackRepositoryImplTest` — SQLDelight integration (in-memory): insert, setVisible, deleteById, selectAll
- `TrackRecordingRepositoryImplTest` — start/stop/addPoint: state transitions, distance accumulation
- `TrackRecordingLabelFormatterTest` — duration label, distance label edge cases
- `GeoMarksListViewModelTest` extension — `showRecordedTracks` toggle, recorded track item mapping
- Manual smoke test: start recording, background app, return after 30 s, stop, verify track in Marks list

---

### Phase 5 — Integration Review

**Tasks**:
- Verify `domain/track/` has zero Android/Compose imports
- Verify `GpsService` depends only on domain interfaces
- Verify `MainViewModel` depends only on use cases
- Verify sheet mutual exclusion is correct: opening track recording sheet calls full `closeGeoMarksSheet()` — mark tool deactivated, pending points cleared

**Skill**: `/architect review: domain/track/, data/track/, service/GpsService.kt, presentation/feature/main/MainViewModel.kt`

---

### Phase 6 — Skill Update Review

**`/architect`**: Add `GpsService` manual `CoroutineScope` pattern for background work. Add sheet mutual exclusion pattern (two sheets, `BottomStart` + `BottomEnd`, exclusive via ViewModel).

**`/ui-designer`**: Add `TrackRecordingSheet` layout decisions — preset chip row, collapsed status row, `BottomStart` + `BottomEnd` coexistence. Add "sheet visibility ≠ feature active" pattern (sheet closed but recording continues; HUD button = only indicator).

**`/icon-designer`**: Add `ic_track_record` style decisions from Phase 2.

**`/planner`**: No methodology changes expected.

---

### Phase 6b — Project Docs & Memory Update

- Add `| Запись трека | ✅ Done |` to CLAUDE.md status table
- Create `.claude/docs/track-recording.md`; include a **"Calibration note"** section in *Known limitations / planned extensions*: preset default values (interval + min distance per preset) were chosen before device testing — after real-world testing these constants should be reviewed and can be adjusted directly in `TrackRecordingPreset` defaults or via a prompt.
- Move plan to `.claude/archive/track-recording.md`; delete from `plans/`; verify with `ls .claude/plans/`
- Update `memory/project_state.md`
- Token log: append to archived plan

---

### Phase 7 — Commit Preparation

Stage by name, propose commit message in Russian, wait for confirmation.

---

## Coordination Map

```
Phase 0:  — (skipped)
Phase 1:  /architect feature: track recording … → [/compact]
Phase 2:  /icon-designer create: ic_track_record
Phase 3:  [direct coding, 15 tasks] → /simplify              ✅ Done
Phase 3c: [direct coding — service robustness]
Phase 3d: [direct coding — background reminder notification]
Phase 4:  [direct coding — tests]
Phase 5:  /architect review: domain/track/, data/track/, GpsService.kt, MainViewModel.kt
Phase 6:  [skill updates: /architect, /ui-designer, /icon-designer, /planner]
Phase 6b: [CLAUDE.md, .claude/docs/track-recording.md, archive plan, memory/]
Phase 7:  [stage by name → propose commit → wait → git commit]
```

---

## Open Questions

1. **Preset default values**: confirm the table above (interval + min distance per preset) is sensible for your use cases, or adjust before Phase 1.
2. **`GeoTrackDistance` reuse**: `latLongToMeter` lives in `domain/marker/util/GeoTrackDistance.kt`. Should it be moved to a shared `domain/util/` package, or copied to `domain/track/util/`? Architect to decide.

---

## Change Log

- 2026-06-01: created; Q2–Q5 from user review incorporated (presets, mutual exclusion, sheet-close ≠ stop, GpsService scope)
- 2026-06-02: added Phase 3c (service robustness: finishIfRecording on onDestroy + orphan recovery with 12-hour deadline); added Phase 3d (background reminder notification every 2 h via ProcessLifecycleOwner)
