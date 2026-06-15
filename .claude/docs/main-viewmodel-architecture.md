# Main Screen ViewModel Architecture

**Date**: 2026-06-15
**Status**: Implemented

## Overview

`MainViewModel` was split from a ~1590-line monolith with 40+ injected dependencies into five focused ViewModels plus a pure `HudStateMapper`. No user-facing behaviour changed.

---

## ViewModel Split

| ViewModel | Owns | Key state |
|---|---|---|
| `MainViewModel` | Map/GPS/camera/orientation/menu | `tileUrlTemplate`, `initialCameraPosition`, `gpsStatus`, `markerSizeLevel`, `nodeMarkers`, `recordedTracks`, `isFollowMeActive`, `isCourseUpActive`, `mapBearing`, `menuDrawerOpen` |
| `ConnectionViewModel` | BLE scan, auto-connect, provisioning, callsign gate | `connectionStatus`, `foundDevices`, `syncRequired`, `callsignRequired`, `isRebooting`, `syncCyclePhase`, `showConnectionLabel`, `networkEnabled` |
| `GeoMarkViewModel` | Marks form, CRUD, sheet state, map tap routing | `markToolActive`, `pendingMarkPoints`, `geoMarks`, `selectedGeoMarkId`, `deleteConfirmMarkId`, `GeoMarksFormState` |
| `TrackRecordingViewModel` | Recording form, stop dialog, exit-on-stop flow | `TrackRecordingFormState`, `StopDialogState`, `_pendingExitOnStop`, `exitAppEvent` |
| `EmergencyViewModel` | SOS state + dialogs | `isSosActive`, `showSosRestoredDialog`, `showSosTriggerDialog`, `showSosCancelDialog` |

### Architecture rule: no VM-to-VM dependencies

ViewModels communicate **only** through domain repositories and use cases — never by injecting another ViewModel. Cross-cutting coordination (exit-on-stop, HUD assembly) happens in the **View layer** (`NavGraph.kt` / `MainScreen`).

---

## HudStateMapper

`presentation/feature/main/osd/HudStateMapper.kt`

Pure `object` — no coroutines, no side-effects. Assembles composite HUD state from multiple VM states.

```kotlin
object HudStateMapper {
    fun buildHudConfig(mainState: MainUiState, connState: ConnectionUiState, nav: HudNavCallbacks): HudConfig
    fun buildHudUiState(mainState: MainUiState, connState: ConnectionUiState, geoMarkState: GeoMarkUiState, trackState: TrackRecordingState, nav: HudNavCallbacks): HudUiState
    fun buildMenuDrawerUiState(mainState: MainUiState, connState: ConnectionUiState, nav: HudNavCallbacks): MenuDrawerUiState
}
```

---

## NavGraph Wiring

`HudConfig`, `HudUiState`, `MenuDrawerUiState` are built in `NavGraph.kt` via `remember(key)` — **not** as StateFlow in ViewModel:

```kotlin
composable<Route.Main> {
    val mainState  by mainVm.uiState.collectAsState()
    val connState  by connectionVm.uiState.collectAsState()
    val geoState   by geoMarkVm.uiState.collectAsState()
    val trackState by trackVm.recordingState.collectAsState()

    val hudConfig  = remember(mainState, connState)                       { HudStateMapper.buildHudConfig(mainState, connState, navCallbacks) }
    val hudUiState = remember(mainState, connState, geoState, trackState) { HudStateMapper.buildHudUiState(mainState, connState, geoState, trackState, navCallbacks) }
    val menuState  = remember(mainState, connState)                       { HudStateMapper.buildMenuDrawerUiState(mainState, connState, navCallbacks) }
}
```

Sheet states (`GeoMarksSheetUiState`, `TrackRecordingSheetUiState`) **remain as `StateFlow`** inside their respective VMs — they contain lambdas and need reactivity independent of NavGraph recomposition.

---

## Exit-on-Stop Coordination

`TrackRecordingViewModel` owns `_pendingExitOnStop` and `exitAppEvent`. `MainScreen` calls:

```kotlin
trackVm.requestExitIfSafe { exitApp() }
```

`MainViewModel` has no involvement in the exit flow.

---

## Background Observers

| Observer | Lives in |
|---|---|
| `ingestReceivedGeoMarks.observe()` | `GeoMarkViewModel.init` |
| `autoExpireGeoMarks.observe()` | `GeoMarkViewModel.init` |
| `ingestReceivedChatMessages.observe()` | `MainViewModel.init` |
| `syncEmergencyMute.observe()` | `EmergencyViewModel.init` |

---

## DI Registration

All five VMs registered as `viewModel {}` (activity-scoped) in `PresentationModule`. `NavGraph.kt` resolves them with separate `koinViewModel<T>()` calls.

---

## Clean Architecture Fixes Applied (Phase 4)

| Violation | Fix |
|---|---|
| `GeoMarkViewModel` imported `data.marker.adapter.GeoMarkWaypointAdapter` | Moved to `domain/marker/util/WaypointIdConverter` |
| `TrackRecordingViewModel` injected `data.track.datasource.TrackSettingsDataSource` | Extracted `domain/track/repository/TrackSettingsRepository` interface |
| `TrackRecordingViewModel` injected `domain.gps.repository.GpsRepository` directly | Replaced with `ObserveGpsLocationUseCase` |
| `HudStateMapper` called `mesh.ble.toMeshtasticDisplayShortName` | Extracted to `presentation/util/MeshtasticDisplayFormatter` |

---

## Files

```
app/src/main/.../presentation/feature/main/
    MainViewModel.kt
    ConnectionViewModel.kt
    GeoMarkViewModel.kt
    TrackRecordingViewModel.kt
    EmergencyViewModel.kt
    osd/
        HudStateMapper.kt
        models/
            ConnectionUiState.kt
            GeoMarkUiState.kt
            EmergencyUiState.kt
            TrackRecordingState.kt
```
