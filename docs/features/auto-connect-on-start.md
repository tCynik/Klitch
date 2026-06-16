# Auto-Connect on App Start

## What it does
BLE scanning starts automatically on launch (after permissions). If the last used node is found, it connects without user action. HUD reflects scan/connect state in real time.

## Key classes
- `MainViewModel.startAutoConnect()` — called from `init{}`; restarts scan loop until connected
- `MeshConnectionRepositoryImpl` — `_isScanning: MutableStateFlow<Boolean>` + `activeScanCount: AtomicInteger`; emits `MeshConnectionStatus.Scanning`
- `ConnectToMeshDeviceUseCase` — **single place** that calls `LastConnectedDeviceRepository.save()` on every connection
- `LastConnectedDeviceRepository` / `LastConnectedDeviceRepositoryImpl` — persists last connected device address via `multiplatform-settings`; `domain/mesh/`, `data/local/mesh/`
- `KableBleScanner` — software UUID filter on `advertisement.uuids`; address-only hardware filter

## Non-obvious decisions — critical invariants
- **Hardware ScanFilter must NOT include service UUID**: Meshtastic nodes advertise service UUID in the scan response packet, not the primary advertisement. Hardware filter evaluates before scan response arrives → device silently dropped on first pass. Software filter on `advertisement.uuids` is immune (assembles from both packets).
- **`activeScanCount: AtomicInteger`**: `MainViewModel` and `MeshTestViewModel` can run `scanDevices()` concurrently. `_isScanning = false` must only be set when `activeScanCount.decrementAndGet() == 0`.
- **`saveLastConnectedDevice` lives in `ConnectToMeshDeviceUseCase`, not in callers**: all connect paths (auto-connect, MeshTest, future screens) converge here. Moving it to callers recreates the circular dependency bug (Bug 5).
- **`onCompletion` must NOT read `_uiState.connectionStatus`**: at the moment `onCompletion` fires, `_isScanning` is already `false` but the `observeConnectionStatus` collector hasn't processed the resulting `Disconnected` emission yet — the status is stale. Condition: `cause == null && !autoConnectAttempted`.
- `foundDevices: ImmutableList<MeshDeviceModel>` accumulates across scan restarts (via `distinctBy { it.address }`); cleared only on `Connecting`/`Connected`.

## Known limitations / planned extensions
- Device selection UI (for "other nodes found" case) is in MeshTest Connection tab — dedicated radio/connection screen is planned

## Source
Plan: `docs/archive/auto-connect-on-start.md`
