# Plan: Auto-connect on App Start

**Date**: 2026-04-16
**Status**: Approved

---

## Summary

On app launch (after BLE permissions are granted) BLE scanning starts automatically.
If the last used node is found — connect to it automatically.
If other nodes are found but not the last one — HUD shows "select node" (yellow).
If no nodes are found — scanning restarts, HUD shows "Searching…" (red).
If scanning is not active (future feature: stopped by user) — red Radio button only, info slot empty.

---

## Permissions

`BlePermissionGuard` in `NavGraph` already handles all BLE/Location permissions **before** any content is rendered.
`MainScreen` (and therefore `MainViewModel`) is created only after permissions are granted → no additional permission request code needed.

---

## HUD State Behaviour

| State | Radio button color | Info slot |
|---|---|---|
| Scan active, no nodes found | Red | "Searching…" |
| Scan active, other nodes found (not the last one) | Yellow | "select node" |
| Connecting | Yellow | "Pairing with $deviceName" |
| Connected (first 2 s) | Green | "Paired with $shortName" |
| Connected (after auto-hide) | Green | — |
| Disconnected / scan not active | Red | — |

---

## Architecture

### Key decisions

**1. `MeshConnectionStatus.Scanning` is now actually emitted**
`MeshConnectionRepositoryImpl` adds `_isScanning: MutableStateFlow<Boolean>`.
The `connectionStatus` flow is extended to `combine(…, _isScanning)`:
if `_isScanning == true` && `serviceState == Disconnected` → emit `MeshConnectionStatus.Scanning`.
`scanDevices()` sets `_isScanning = true` on entry and `false` in `finally`.

**2. `foundOtherDevicesDuringScan` in `MainUiState`**
The presentation layer tracks whether nodes were found during scan that are not the last device.
Managed by `MainViewModel` inside the auto-connect coroutine.

**3. Last connected device stored in `Settings` (multiplatform-settings)**
Same mechanism as `LastMapPositionRepositoryImpl`.
Keys: `last_ble_address`, `last_ble_name`.

**4. Auto-connect flow in `MainViewModel`**
Started from `init{}` via `startAutoConnect()`.
Cancelled from the `observeConnectionStatus` collector when status transitions to `Connecting` / `Connected`.
On successful connection — saves the device as the new "last connected".
If scan ends naturally (30 s timeout) with no nodes found — restarts scan.

---

## Affected Files

### New files

| File | Purpose |
|---|---|
| `domain/mesh/repository/LastConnectedDeviceRepository.kt` | Repository interface for last-node persistence |
| `domain/mesh/usecase/GetLastConnectedDeviceUseCase.kt` | Synchronous get |
| `domain/mesh/usecase/SaveLastConnectedDeviceUseCase.kt` | Suspend save |
| `data/local/mesh/LastConnectedDeviceRepositoryImpl.kt` | `Settings`-based implementation |

### Modified files

| File | Change |
|---|---|
| `data/mesh/repository/MeshConnectionRepositoryImpl.kt` | Add `_isScanning` + expand `connectionStatus` combine + wrap `scanDevices()` in try/finally |
| `presentation/feature/main/MainUiState.kt` | Add `foundOtherDevicesDuringScan: Boolean = false` |
| `presentation/feature/main/MainViewModel.kt` | Auto-connect logic + update Radio button color/info |
| `di/MeshDataModule.kt` | Bind `LastConnectedDeviceRepository` + use cases |
| `di/PresentationModule.kt` | Add new use cases to `MainViewModel` constructor |

---

## Phase Plan

### Phase 1 — Domain: Last Connected Device

**File**: `domain/mesh/repository/LastConnectedDeviceRepository.kt`
```kotlin
interface LastConnectedDeviceRepository {
    fun get(): MeshDeviceModel?
    suspend fun save(device: MeshDeviceModel)
}
```

**File**: `domain/mesh/usecase/GetLastConnectedDeviceUseCase.kt`
```kotlin
class GetLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) {
    operator fun invoke(): MeshDeviceModel? = repository.get()
}
```

**File**: `domain/mesh/usecase/SaveLastConnectedDeviceUseCase.kt`
```kotlin
class SaveLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) : UseCase<MeshDeviceModel, Unit>() {
    override suspend fun invoke(params: MeshDeviceModel) = repository.save(params)
}
```

---

### Phase 2 — Data: Last Connected Device Persistence

**File**: `data/local/mesh/LastConnectedDeviceRepositoryImpl.kt`

```kotlin
private const val KEY_ADDRESS = "last_ble_address"
private const val KEY_NAME    = "last_ble_name"

class LastConnectedDeviceRepositoryImpl(
    private val settings: Settings,
) : LastConnectedDeviceRepository {

    override fun get(): MeshDeviceModel? {
        val address = settings.getStringOrNull(KEY_ADDRESS) ?: return null
        val name    = settings.getStringOrNull(KEY_NAME) ?: address
        return MeshDeviceModel(address = address, name = name, rssi = 0)
    }

    override suspend fun save(device: MeshDeviceModel) {
        settings.putString(KEY_ADDRESS, device.address)
        settings.putString(KEY_NAME, device.name)
    }
}
```

---

### Phase 3 — Data: `MeshConnectionRepositoryImpl` — emit Scanning

**Changes**:

1. Add field:
   ```kotlin
   private val _isScanning = MutableStateFlow(false)
   ```

2. Expand `connectionStatus` from `combine(3 flows)` to `combine(4 flows)`:
   ```kotlin
   override val connectionStatus: Flow<MeshConnectionStatus> =
       combine(
           serviceRepository.connectionState.onEach { … },
           nodeRepository.ourNodeInfo,
           radioInterfaceService.bleRssi,
           _isScanning,
       ) { state, node, bleRssi, isScanning ->
           if (isScanning && state == ConnectionState.Disconnected) {
               MeshConnectionStatus.Scanning
           } else {
               state.toMeshConnectionStatus(node, pendingDeviceName, bleRssi)
           }
       }
   ```

3. Wrap `scanDevices()` body in `try/finally`:
   ```kotlin
   override fun scanDevices(): Flow<List<MeshDeviceModel>> = flow {
       _isScanning.value = true
       try {
           val discovered = mutableListOf<MeshDeviceModel>()
           bleScanner.scan(timeout = 30.seconds, serviceUuid = …).collect { device ->
               if (discovered.none { it.address == device.address }) {
                   discovered.add(MeshDeviceModel(…))
                   emit(discovered.toList())
               }
           }
       } finally {
           _isScanning.value = false
       }
   }
   ```

---

### Phase 4 — Presentation: `MainUiState`

Add field:
```kotlin
val foundOtherDevicesDuringScan: Boolean = false,
```

---

### Phase 5 — Presentation: `MainViewModel`

#### 5a — New constructor parameters

```kotlin
private val scanDevices: ScanMeshDevicesUseCase,
private val connectToDevice: ConnectToMeshDeviceUseCase,
private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
private val saveLastConnectedDevice: SaveLastConnectedDeviceUseCase,
```

#### 5b — New field

```kotlin
private var scanJob: Job? = null
```

#### 5c — `init{}`: kick off auto-connect

At the end of `init {}` add:
```kotlin
startAutoConnect()
```

#### 5d — `observeConnectionStatus` collector: cancel scan on connect

At the top of the existing `.onEach { status -> … }` block add:
```kotlin
if (status is MeshConnectionStatus.Connecting || status is MeshConnectionStatus.Connected) {
    scanJob?.cancel()
    scanJob = null
    _uiState.update { it.copy(foundOtherDevicesDuringScan = false) }
}
```

Note: the last-device save happens in `startAutoConnect()` before `connectToDevice()` is called, so no save is needed here.

#### 5e — `startAutoConnect()`

```kotlin
private fun startAutoConnect() {
    val lastDevice = getLastConnectedDevice()
    scanJob?.cancel()
    var autoConnectAttempted = false

    scanJob = scanDevices(NoParams)
        .onEach { devices ->
            if (autoConnectAttempted) return@onEach
            val target = lastDevice?.let { last -> devices.find { it.address == last.address } }
            if (target != null) {
                autoConnectAttempted = true
                _uiState.update { it.copy(foundOtherDevicesDuringScan = false) }
                viewModelScope.launch {
                    saveLastConnectedDevice(target)
                    connectToDevice(ConnectToMeshDeviceParams(target.address, target.name))
                }
                scanJob?.cancel()
            } else {
                _uiState.update { it.copy(foundOtherDevicesDuringScan = devices.isNotEmpty()) }
            }
        }
        .onCompletion { cause ->
            // Flow completed naturally (30 s timeout), NOT via cancellation
            if (cause == null && !autoConnectAttempted) {
                val currentStatus = _uiState.value.connectionStatus
                val foundOthers   = _uiState.value.foundOtherDevicesDuringScan
                // No nodes found at all — restart scan
                if (currentStatus is MeshConnectionStatus.Disconnected && !foundOthers) {
                    startAutoConnect()
                }
                // Other nodes found — leave "select node" visible, wait for user action
            }
        }
        .catch { /* CancellationException — normal job termination, ignored */ }
        .launchIn(viewModelScope)
}
```

#### 5f — `buildNodeStatusColor()`: update Scanning branch

```kotlin
MeshConnectionStatus.Scanning ->
    if (state.foundOtherDevicesDuringScan) Color.Yellow else Color.Red
is MeshConnectionStatus.Connecting -> Color.Yellow
```

#### 5g — `buildConnectionInfoSlot()`: update Scanning branch

```kotlin
MeshConnectionStatus.Scanning ->
    if (state.foundOtherDevicesDuringScan)
        HudInfoSlot(content = "select node", color = Color.Yellow)
    else
        HudInfoSlot(content = "Searching...", color = Color.Red)
```

---

### Phase 6 — DI

#### `MeshDataModule.kt`

Add to the `single { … }` block:
```kotlin
single<LastConnectedDeviceRepository> { LastConnectedDeviceRepositoryImpl(get<Settings>()) }
single { GetLastConnectedDeviceUseCase(get()) }
single { SaveLastConnectedDeviceUseCase(get()) }
```

#### `PresentationModule.kt`

In `viewModel { MainViewModel(…) }` add:
```kotlin
scanDevices = get(),
connectToDevice = get(),
getLastConnectedDevice = get(),
saveLastConnectedDevice = get(),
```

---

### Phase 7 — Simplify

Run `/simplify` on changed files:
- `MeshConnectionRepositoryImpl.kt`
- `MainViewModel.kt`
- `MainUiState.kt`

---

### Phase 8 — Integration Review

- `scanJob` does not leak: cancelled automatically by `viewModelScope` in `onCleared()` ✅
- `_isScanning` belongs to a singleton repository — verify no races from concurrent `scanDevices()` calls (currently only one call site: `MainViewModel`)
- `onCompletion` fires only on natural completion, not on `CancellationException` ✅

---

### Phase 9 — Docs & Memory Update

- CLAUDE.md: add "Auto-connect on start" feature → Done (after implementation)
- Set this plan status → Done
- Append entry to Change Log

---

### Phase 10 — Commit Preparation

1. `git status` — list changed files
2. Stage by name (never `git add -A`)
3. Draft commit message in Russian, imperative mood
4. Present staged files + message → wait for confirmation → `git commit`

---

## Coordination Map

```
Phase 1:  [direct coding] — domain: repository interface + use cases
Phase 2:  [direct coding] — data: Settings-based persistence
Phase 3:  [direct coding] — data: MeshConnectionRepositoryImpl + _isScanning
Phase 4:  [direct coding] — presentation: MainUiState
Phase 5:  [direct coding] — presentation: MainViewModel auto-connect logic
Phase 6:  [direct coding] — DI wiring
Phase 7:  /simplify
Phase 8:  [direct review]
Phase 9:  [docs & memory]
Phase 10: [stage → commit]
```

---

## Open Questions

- None.

---

## Change Log

- 2026-04-16: created
