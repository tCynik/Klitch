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

## Tests

- `NetworkViewModelCallsignGateTest` — callsign gate + scan blocked when network disabled
