# Plan: MainViewModel Split into Feature ViewModels

**Date**: 2026-06-15
**Status**: Approved (Phase 1–3 complete)

## Summary

`MainViewModel` has grown to ~1590 lines with 40+ injected dependencies spanning five unrelated
domains: BLE connection management, geo marks form/CRUD, track recording, emergency SOS, and
map/GPS/HUD orchestration. The goal is to split it into four focused feature ViewModels plus a
slimmed coordinator, extract HUD builders into a pure `HudStateMapper`, and update `MainScreen` to
collect from multiple VMs. No new user-facing behaviour; the split is purely structural.

## Scope

**In scope:**
- Extract `ConnectionViewModel` (BLE scan, auto-connect, provisioning, sync check, callsign gate)
- Extract `GeoMarkViewModel` (marks form, CRUD, sheet state, map tap routing for marks)
- Extract `TrackRecordingViewModel` (recording form, stop dialog, exit-on-stop flow)
- Extract `EmergencyViewModel` (SOS state + dialogs)
- Slim `MainViewModel` to map/GPS/camera/orientation/HUD orchestration
- Extract `HudStateMapper` — pure object mapping all VM states → `HudConfig` / `HudUiState`
- Update `PresentationModule` (Koin) to register new VMs
- Update `MainScreen` to collect from multiple VMs

**Out of scope:**
- Changing any UI behaviour or visual design
- Modifying domain or data layers
- Adding tests (deferred — Phase 4 is explicitly out of scope for this plan)
- Splitting other feature ViewModels (ChatViewModel, SettingsViewModel, etc.)

---

## Domain Map (current MainViewModel)

| Domain | Key state fields | Key deps |
|---|---|---|
| Connection | `connectionStatus`, `foundDevices`, `syncRequired`, `callsignRequired`, `isRebooting`, `syncCyclePhase`, `showConnectionLabel`, `networkEnabled` | `ConnectToMeshDeviceUseCase`, `ScanMeshDevicesUseCase`, `NodeProvisioningUseCase`, `CheckNodeSyncUseCase`, `ContourSyncStateRepository`, `RebootStateRepository`, `ObserveCallsignChangesUseCase`, `RefreshNodePublicKeyUseCase`, `ObserveAppUserUseCase`, `ObserveNetworkEnabledUseCase`, `ObserveNodeChannelsUseCase` |
| Geo Marks | `markToolActive`, `pendingMarkPoints`, `trackDraftDistanceLabel`, `geoMarks`, `selectedGeoMarkId`, `deleteConfirmMarkId`, `GeoMarksFormState` | `ObserveGeoMarksUseCase`, `SendGeoMarkUseCase`, `ToggleGeoMarkVisibilityUseCase`, `DeleteGeoMarksUseCase`, `IngestReceivedGeoMarksUseCase`, `AutoExpireGeoMarksUseCase`, `GeoMarkPreferencesRepository`, `ObserveContoursUseCase` |
| Track Recording | `TrackRecordingFormState`, `StopDialogState`, `_pendingExitOnStop` | `StartTrackRecordingUseCase`, `PauseTrackRecordingUseCase`, `ResumeTrackRecordingUseCase`, `StopTrackRecordingUseCase`, `DiscardTrackRecordingUseCase`, `UpdateTrackRecordingNameUseCase`, `UpdateTrackRecordingColorUseCase`, `TrackSettingsDataSource`, `GpsRepository`, `ObserveTrackRecordingStateUseCase` |
| Emergency SOS | `isSosActive`, `showSosRestoredDialog`, `showSosTriggerDialog`, `showSosCancelDialog` | `ObserveEmergencyModeUseCase`, `TriggerEmergencyUseCase`, `CancelEmergencyUseCase`, `SyncEmergencyMuteUseCase` |
| Map / HUD | `tileUrlTemplate`, `initialCameraPosition`, `gpsStatus`, `markerSizeLevel`, `geoMarkSizeLevel`, `showGeoMarkNames`, `selectedOverlays`, `nodeMarkers`, `recordedTracks`, `isFollowMeActive`, `isCourseUpActive`, `zoomAtCourseUpActivation`, `isNorthLocked`, `mapBearing`, `menuDrawerOpen` | `GetTileUrlUseCase`, `GetLastMapPositionUseCase`, `SaveLastMapPositionUseCase`, `ObserveNodeMarkersUseCase`, `ObserveGpsStatusUseCase`, `ObserveMarkerSizeLevelUseCase`, `ObserveGeoMarkSizeLevelUseCase`, `ObserveShowGeoMarkNamesUseCase`, `ObserveSelectedOverlaysUseCase`, `ObserveRecordedTracksUseCase`, `ObserveRecordedTrackPointsUseCase`, `ObserveTrackRecordingStateUseCase`, `ObserveTotalUnreadChatCountUseCase`, `IngestReceivedChatMessagesUseCase` |

---

## Architecture Notes

### Rule: no VM-to-VM dependencies

ViewModels communicate **only** through domain repositories and use cases — never by injecting
another ViewModel. Cross-cutting coordination (exit-on-stop, HUD assembly) happens in the **View
layer** (`MainScreen`).

### HUD assembly moves to `HudStateMapper`

`buildHudConfig`, `buildHudUiState`, `buildLeftColumn`, `buildRightColumn`,
`buildConnectionInfoSlot`, `buildNodeStatusColor`, `buildMenuDrawerUiState`,
`buildCompassButton`, `buildGeoMarksSheetUiState`, `buildTrackRecordingSheetUiState` are pure
functions of their input state — no coroutines, no side-effects. They move to
`osd/HudStateMapper.kt` as top-level functions or an `object`. This alone removes ~300 lines
from the ViewModel layer.

`MainScreen` then does:
```kotlin
val hudConfig = HudStateMapper.buildHudConfig(mainState, connectionState, navCallbacks)
val hudUiState = HudStateMapper.buildHudUiState(mainState, connectionState, geoMarkState,
    trackState, navCallbacks)
```

### Exit-on-stop coordination

`requestExitApp` currently checks track recording state and triggers the stop dialog.
After split, `MainScreen` calls `trackRecordingViewModel.requestExitIfSafe { exitAppEvent.emit() }`.
`TrackRecordingViewModel` owns `_pendingExitOnStop` and `exitAppEvent`; `MainViewModel` is
removed from that flow.

### Background observers

| Observer | Moves to |
|---|---|
| `ingestReceivedGeoMarks.observe()` | `GeoMarkViewModel.init` |
| `autoExpireGeoMarks.observe()` | `GeoMarkViewModel.init` |
| `ingestReceivedChatMessages.observe()` | `MainViewModel.init` (it only outputs to repos, no state) |
| `syncEmergencyMute.observe()` | `EmergencyViewModel.init` |

### Koin scoping

All five VMs registered as `viewModel {}` (activity-scoped, default) in `PresentationModule`.
`MainScreen` resolves them with separate `viewModel<T>()` calls. No shared scope is needed because
all shared state is already in the repository layer.

---

## Phase Plan

### Phase 1 — Architecture Review (before coding starts)

**Goal**: confirm the domain split and inter-VM boundaries before writing code.

**Tasks:**
- Invoke `/architect` with this plan as input
- Validate: are there hidden cross-domain state reads that break the "no VM-to-VM" rule?
- Confirm `HudStateMapper` signature (what parameters each builder receives)
- Confirm `MainUiState` residual shape after removing migrated fields

**Skill**: `/architect task: review MainViewModel split plan — validate domain boundaries, HudStateMapper signature, exit-on-stop coordination`

**Output**: Approved architecture notes (or amendments to this plan)

---

### Phase 2 — Implementation (in dependency order)

**Goal**: all five VMs functional, `MainScreen` updated, `MainViewModel` slim.

Execute in this order to allow incremental verification after each step:

#### Step 2.1 — Extract `HudStateMapper`
- Create `presentation/feature/main/osd/HudStateMapper.kt`
- Move all `buildXxx` private functions out of `MainViewModel` as top-level functions
- Keep `MainViewModel` calling them (temporarily) to confirm no regression
- Signatures take explicit state parameters (not `MainUiState` whole — to survive subsequent steps)

#### Step 2.2 — Extract `EmergencyViewModel`
Simplest domain (3 use cases, 4 state fields). Extract first to validate the pattern.

New file: `presentation/feature/main/EmergencyViewModel.kt`

State:
```kotlin
data class EmergencyUiState(
    val isSosActive: Boolean = false,
    val showSosRestoredDialog: Boolean = false,
    val showSosTriggerDialog: Boolean = false,
    val showSosCancelDialog: Boolean = false,
)
```
Deps: `ObserveEmergencyModeUseCase`, `TriggerEmergencyUseCase`, `CancelEmergencyUseCase`,
`SyncEmergencyMuteUseCase`

Methods: `onSosButtonClick`, `onSosRestoredKeep`, `onSosRestoredDisable`,
`onTriggerSosConfirm`, `onCancelSosConfirm`, `onDismissSosDialog`

Remove from `MainViewModel` + `MainUiState`.
Add to `PresentationModule`.
Update `MainScreen` to resolve and collect `EmergencyViewModel`.

#### Step 2.3 — Extract `ConnectionViewModel`

New file: `presentation/feature/main/ConnectionViewModel.kt`

State:
```kotlin
data class ConnectionUiState(
    val connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    val showConnectionLabel: Boolean = false,
    val foundDevices: ImmutableList<MeshDeviceModel> = persistentListOf(),
    val syncRequired: Boolean = false,
    val callsignRequired: Boolean = false,
    val isRebooting: Boolean = false,
    val syncCyclePhase: NodeSyncCyclePhase = NodeSyncCyclePhase.Idle,
    val networkEnabled: Boolean = true,
)
```

Deps: see Domain Map above.

Private: `scanJob`, `connectedLabelJob`, `startAutoConnect()`, `startAutoConnectIfEnabled()`.

Remove from `MainViewModel` + `MainUiState`.
Add to `PresentationModule`.
Update `MainScreen` + `HudStateMapper` calls.

#### Step 2.4 — Extract `GeoMarkViewModel`

New file: `presentation/feature/main/GeoMarkViewModel.kt`

Owns:
- `_geoMarkUiState` (markToolActive, pendingMarkPoints, trackDraftDistanceLabel, geoMarks,
  selectedGeoMarkId, deleteConfirmMarkId)
- `_formState: MutableStateFlow<GeoMarksFormState>`
- `geoMarksSheetUiState: StateFlow<GeoMarksSheetUiState>` (built by `HudStateMapper`)
- `contextMenuEvent: SharedFlow<GeoMarkContextMenuEvent>`

All `setMarkXxx`, `sendPendingMark`, `clearPendingPoints`, `deletePendingPoint`,
`onMapClick`, `onMapDoubleClick`, `onMapLongClick`, `toggleMarkTool`, `toggleGeoMarksSheet`,
`closeGeoMarksSheet`, `toggleSheetCollapsed`, `hideGeoMark`, `requestDeleteGeoMark`,
`confirmDeleteGeoMark`, `dismissDeleteGeoMarkConfirm`, `prepareGeoMarkForResend`,
`clearSelectedGeoMark`, `applyPreset` move here.

Private: `sendGeoMarkAtPoints`, `buildMarkLabel`, `persistFormState`, `savePreset`,
`applyPrefsToFormState`, `findNearestVisibleMarkId`, `distanceSqMeters`.

Remove from `MainViewModel` + `MainUiState`.
Add to `PresentationModule`.
Update `MainScreen` map gesture handlers to call `geoMarkViewModel.onMapClick(...)`.

#### Step 2.5 — Extract `TrackRecordingViewModel`

New file: `presentation/feature/main/TrackRecordingViewModel.kt`

Owns:
- `_trackFormState: MutableStateFlow<TrackRecordingFormState>`
- `_stopDialogState: MutableStateFlow<StopDialogState>`
- `_pendingExitOnStop: MutableStateFlow<Boolean>`
- `trackRecordingSheetUiState: StateFlow<TrackRecordingSheetUiState>` (via `HudStateMapper`)
- `exitAppEvent: SharedFlow<Unit>`
- `trackNoMovementDiscardedEvent: SharedFlow<Unit>`

All `setTrackXxx`, `startTrackRecordingAction`, `pauseTrackRecordingAction`,
`resumeTrackRecordingAction`, `stopTrackRecordingAction`, `confirmTrackStopSave`,
`confirmTrackStopDiscard`, `cancelTrackStopDialog`, `setStopDialogTrimToMovement`,
`toggleTrackRecordingSheet`, `closeTrackRecordingSheetVisibility`, `toggleTrackSheetCollapsed`
move here.

New method: `requestExitIfSafe(onSafeToExit: suspend () -> Unit)` — replaces `requestExitApp`.
Called from `MainScreen` which owns the exit flow.

Remove from `MainViewModel`.
Add to `PresentationModule`.
Update `MainScreen`.

#### Step 2.6 — Slim `MainViewModel`

After steps 2.2–2.5, `MainViewModel` retains only:
- `MainUiState` (camera, GPS, markers, settings, overlays, recorded tracks, orientation, menu drawer)
- `hudConfig: StateFlow<HudConfig>`
- `hudUiState: StateFlow<HudUiState>`
- `menuDrawerUiState: StateFlow<MenuDrawerUiState>`
- `resetBearingEvent`, `restoreZoomEvent`
- `_navCallbacks`, `provideNavCallbacks`
- `toggleMenuDrawer`, `onFollowMeToggle/Deactivated`, `onCompassTap`, `onCourseUpToggle/Deactivated`,
  `onFollowMeRestoreZoom`, `onMapBearingChanged`, `onMapRotatedByUser`, `onCameraPositionChanged`
- `ingestReceivedChatMessages.observe()` (no-op observer, stays here)
- `onMainDestinationVisible()`

HUD `combine()` flows now take `connectionState: ConnectionUiState` and
`geoMarkFormVisible: Boolean` as extra parameters, provided by `MainScreen` via `HudStateMapper`
directly (not combined inside MainViewModel).

#### Step 2.7а — Обновить сигнатуру `MainScreen`

Заменить `uiState: MainUiState` на три отдельных параметра:
`mainState: MainUiState`, `emergencyState: EmergencyUiState`, `geoMarkUiState: GeoMarkUiState`.

Обновить все места внутри `MainScreen`, где читались `uiState.geoMarks`, `uiState.pendingMarkPoints`,
`uiState.selectedGeoMarkId`, `uiState.deleteConfirmMarkId`, `uiState.markToolActive`,
`uiState.trackDraftDistanceLabel`, `uiState.isSosActive`, `uiState.showSosRestoredDialog`,
`uiState.showSosTriggerDialog`, `uiState.showSosCancelDialog`.

#### Step 2.7б — Обновить `NavGraph`

- Добавить `val trackVm: TrackRecordingViewModel = koinViewModel()` в `NavGraph()`
- Перенести подписки `exitAppEvent` и `trackNoMovementDiscardedEvent` с `mainViewModel` на `trackVm`
- Перенести `TrackStopConfirmDialog` на `trackVm.trackRecordingSheetUiState`
- Обновить `Route.MainSettings`: `onExitApp = { trackVm.requestExitIfSafe { exitApp() } }`
- В `composable<Route.Main>`: резолвить все 5 VM, собирать HUD через `remember(key)` + `HudStateMapper`
- Убрать коллекцию `hudConfig`, `hudUiState`, `menuDrawerUiState`, `geoMarksSheetUiState`,
  `trackRecordingSheetUiState` из `mainViewModel` — теперь из соответствующих VM

---

### Phase 3 — Simplify ✅ Done

Run `/simplify` on all changed files after Phase 2 is complete.

**Applied (2026-06-15):**
- `ConnectionViewModel`: убрана дублирующая проверка `networkEnabled`; `delay` в `onEach` вынесен в child coroutine
- `HudStateMapper`: извлечены `buildSatelliteButton`, `buildRadioButton`, `buildMarksButton`
- `GeoMarkViewModel`: `onMapClick` + `onMapClickWithNodeNames` слиты (default param)
- `TrackRecordingViewModel`: `teardownStop()`, `updateAndPersist()`, `persistCurrentSettings()`
- `MainScreen`: `if/else` для tileUrlTemplate; три `!isLandscape` слиты в один блок
- `NavGraph`: убран двойной `collectAsState`; `navCallbacks` → `remember(navController)` + `rememberUpdatedState`

---

### Phase 4 — Architecture Review

**Goal**: confirm no Clean Architecture violations introduced.

**Skill**: `/architect review: presentation/feature/main/`

**Output**: review report, violations fixed.

---

### Phase 5 — Skill Update Review

Check each skill for updates needed:
- `/architect` — new pattern: HudStateMapper as pure presentation mapper
- `/planner` — no changes needed
- `/ui-designer` — no changes needed
- `/icon-designer` — no changes needed
- `/tester` — update ViewModel test patterns to reflect new scoped VMs

---

### Phase 6 — Docs & Memory Update

- Update `CLAUDE.md` feature table (no new feature, but note MainViewModel is now 5 VMs)
- Create `.claude/docs/main-viewmodel-architecture.md`
- Archive this plan → `.claude/archive/mainviewmodel-split.md`
- Update memory: project state

---

### Phase 7 — Commit

Stage and commit after all phases complete. Commit message in Russian.

---

## Coordination Map

```
Phase 1: /architect task: review split plan
Phase 2: direct coding — Steps 2.1 → 2.2 → 2.3 → 2.4 → 2.5 → 2.6 → 2.7 (sequential)
Phase 3: /simplify on changed files
Phase 4: /architect review: presentation/feature/main/
Phase 5: skill update review
Phase 6: docs & memory update
Phase 7: stage by name → propose commit → wait for confirmation → git commit
```

---

## Architecture Decisions (Phase 1 output)

### OQ-1: HUD assembly ✅ Resolved

`hudConfig` / `hudUiState` / `menuDrawerUiState` **не остаются StateFlow в MainViewModel** — убрать их.

HUD собирается в NavGraph composable через `remember(key)`:

```kotlin
composable<Route.Main> {
    val mainState    by mainVm.uiState.collectAsState()
    val connState    by connectionVm.uiState.collectAsState()
    val geoState     by geoMarkVm.uiState.collectAsState()
    val trackRecState by trackVm.recordingState.collectAsState()

    val navCallbacks = remember(navController) {
        HudNavCallbacks(
            onRadioClick = { navController.navigate(Route.Network) },
            onExitApp    = { trackVm.requestExitIfSafe { exitApp() } },
            // ...
        )
    }
    val hudConfig  = remember(mainState, connState)                          { HudStateMapper.buildHudConfig(mainState, connState, navCallbacks) }
    val hudUiState = remember(mainState, connState, geoState, trackRecState) { HudStateMapper.buildHudUiState(mainState, connState, geoState, trackRecState, navCallbacks) }
    val menuState  = remember(mainState, connState)                          { HudStateMapper.buildMenuDrawerUiState(mainState, connState, navCallbacks) }
}
```

`buildGeoMarksSheetUiState` и `buildTrackRecordingSheetUiState` **остаются реактивными StateFlow** внутри `GeoMarkViewModel` и `TrackRecordingViewModel` — они содержат лямбды и должны обновляться реактивно.

### OQ-2: `unreadChatCount` ✅ Остаётся в `MainViewModel`

### OQ-3: `GetMarkerSizeLevelUseCase` ✅ Остаётся в `MainViewModel` (one-shot init)

### OQ-4: Таймер `trackRecordingSheetUiState` ✅ Без изменений — уже `WhileSubscribed(5_000)`

### HudStateMapper сигнатуры

```kotlin
object HudStateMapper {
    fun buildHudConfig(mainState: MainUiState, connState: ConnectionUiState, nav: HudNavCallbacks): HudConfig
    fun buildHudUiState(mainState: MainUiState, connState: ConnectionUiState, geoMarkState: GeoMarkUiState, trackRecordingState: TrackRecordingState, nav: HudNavCallbacks): HudUiState
    fun buildMenuDrawerUiState(mainState: MainUiState, connState: ConnectionUiState, nav: HudNavCallbacks): MenuDrawerUiState
    // buildGeoMarksSheetUiState и buildTrackRecordingSheetUiState остаются в соответствующих VM
}
```

### MainScreen новая сигнатура

```kotlin
@Composable
fun MainScreen(
    mainState: MainUiState,           // камера, GPS, ориентация, меню
    emergencyState: EmergencyUiState, // isSosActive + SOS-диалоги
    geoMarkUiState: GeoMarkUiState,   // geoMarks, pendingPoints, selectedMarkId, deleteConfirmMarkId
    hudConfig: HudConfig,
    hudUiState: HudUiState,
    menuDrawerUiState: MenuDrawerUiState,
    geoMarksSheetUiState: GeoMarksSheetUiState,
    trackRecordingSheetUiState: TrackRecordingSheetUiState,
    // ... callbacks без изменений
)
```

### NavGraph тоже получает TrackRecordingViewModel

`NavGraph()` резолвит `TrackRecordingViewModel` для:
- `exitAppEvent` (было `mainViewModel.exitAppEvent`)
- `trackNoMovementDiscardedEvent`
- `TrackStopConfirmDialog`
- `onExitApp` в `Route.MainSettings`

## Open Questions

*(Все закрыты в Phase 1 — см. Architecture Decisions выше)*

---

## Change Log

- 2026-06-15: created
- 2026-06-15: Phase 1 complete — architectural review done, all Open Questions resolved, plan amended
- 2026-06-15: Phase 2 complete — все 5 VM извлечены, HudStateMapper создан, MainScreen/NavGraph обновлены
- 2026-06-15: Phase 3 complete — simplify на всех изменённых файлах
- 2026-06-15: Phase 4 complete — архревью + 4 нарушения устранены (WaypointIdConverter в domain, TrackSettingsRepository интерфейс, ObserveGpsLocationUseCase, MeshtasticDisplayFormatter в presentation)
