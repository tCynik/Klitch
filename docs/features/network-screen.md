# Network Screen («Сеть»)

Production screen replacing the debug `MeshTest` prototype. Single scroll layout with connection + telemetry; node config moved to a separate settings screen.

## Routes

| Route | Screen |
|---|---|
| `Route.Network` | Main «Сеть» screen (HUD radio button) |
| `Route.NetworkSettings` | Config + location settings (gear in TopAppBar) |

Both routes are available in all builds (no `BuildConfig.DEBUG` gate).

## Package

```
presentation/feature/network/
├── NetworkScreen.kt
├── NetworkViewModel.kt
├── NetworkUiState.kt
├── NetworkSettingsScreen.kt
├── NetworkSettingsViewModel.kt
├── NetworkSettingsUiState.kt
├── components/
│   ├── MeshStatusBar.kt
│   ├── CallsignGateDialog.kt
│   ├── ConnectionContent.kt
│   ├── TelemetryContent.kt
│   ├── NetworkSettingsContent.kt
│   └── LocationConfigCard.kt
└── state/
    ├── NetworkConnectionState.kt
    ├── NetworkTelemetryState.kt
    ├── NetworkSettingsState.kt
    ├── MeshConnectionStatusUi.kt
    └── models/
        ├── CallsignGateDialogState.kt
        └── LocationConfigUi.kt
```

## networkEnabled toggle

- Persisted in `AppSettings` (`network_enabled`, default `true`)
- Domain: `NetworkSettingsRepository`, `ObserveNetworkEnabledUseCase`, `SetNetworkEnabledUseCase`
- When **off**: no BLE scan/connect; NetworkScreen shows centered placeholder; HUD radio button uses `selected = false` (click still navigates)
- When toggled off: `NetworkViewModel` stops scan and disconnects; `MainViewModel` cancels auto-connect scan

## ViewModels

| VM | Responsibility |
|---|---|
| `NetworkViewModel` | Connection, telemetry, callsign gate, sync dialog, network toggle |
| `NetworkSettingsViewModel` | Device config, channels, location config |

## HUD integration

`MainViewModel` observes `networkEnabled` and sets radio `HudButtonSlot.selected = false` when network is disabled.

## Removed from MeshTest

- Tab row, Messages tab, GeoNodes tab (GeoNodes list kept in legacy `NodesScreen` under `presentation/feature/nodes/`)

## Key files

| Layer | File |
|---|---|
| Domain | `shared/.../NetworkSettingsRepository.kt` |
| Data | `shared/.../AppSettings.kt` (`KEY_NETWORK_ENABLED`) |
| Navigation | `navigation/Route.kt`, `navigation/NavGraph.kt` |
| DI | `di/MapDataModule.kt`, `di/PresentationModule.kt` |

## Connection status indicator

`MeshStatusBar` dot color (`StatusDot`):

| Status | Color |
|---|---|
| `Connected` | Green |
| `Connecting` / `Syncing` / `Rebooting` | Yellow |
| `Scanning` / `WaitingForNode` | Blue |
| `Error` / `Disconnected` | Red |

`Disconnected` is red, not the previous neutral gray — it's only reached after the BLE reconnect loop has given up (via `MeshConnectionManagerImpl`'s sleep timeout), so it signals a real, final connection loss. Transient retries surface as `Connecting`/`DeviceSleep` (yellow), not red.

RSSI shown in the status bar (`MeshConnectionStatus.Connected.rssi`) is refreshed every 15s while connected (`BleRadioInterface.pollRssi`), not just once at connect time — see `docs/debug/network-screen.md` for the reconnect-deadlock bug this was bundled with.

## Telemetry refresh

`onRefreshTelemetryClick()` sends a real `TelemetryType.DEVICE` request to the connected node (`MeshConfigRepository.requestTelemetry()` → `RequestTelemetryUseCase`). `telemetry.isLoading` resets either when fresh telemetry arrives via `observeOurNode`, or after a 10s timeout fallback if the node never responds — it can no longer spin forever.

`NetworkTelemetryState.lastUpdatedAtMillis` tracks when device telemetry was last actually received. The "Telemetry" card header shows `Telemetry (received {m}m {s}s ago)` once data has arrived, ticking every second via a `LaunchedEffect` in `TelemetryContent`; falls back to the plain title when no telemetry has been received yet.

## Tests

- `NetworkViewModelCallsignGateTest` — callsign gate + scan blocked when network disabled
