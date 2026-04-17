# Feature: Auto-connect on App Start + BLE Device Discovery

**Date**: 2026-04-16  
**Status**: Done

---

## Summary

On app launch (after BLE permissions are granted) scanning starts automatically.
- If the last used node is found — connect to it without user action.
- If other nodes are found but not the last one — HUD shows "выбор узла" (yellow); MeshTest Connection tab shows the device list immediately.
- If no nodes found — scan restarts in a loop; HUD shows "Поиск…" (red).

---

## HUD State Behaviour

| State | Radio button colour | Info slot |
|---|---|---|
| Scan active, no nodes found | Red | "Поиск…" |
| Scan active, other nodes found | Yellow | "выбор узла" |
| Connecting | Yellow | "Сопряжение с $deviceName" |
| Connected (first 2 s) | Green | "Сопряжено с $shortName" |
| Connected (after auto-hide) | Green | — |
| Disconnected / scan not active | Red | — |

---

## Architecture

### Key decisions

**1. `MeshConnectionStatus.Scanning` emitted from repository**  
`MeshConnectionRepositoryImpl` has `_isScanning: MutableStateFlow<Boolean>`.  
`connectionStatus` is `combine(serviceState, ourNodeInfo, bleRssi, _isScanning)`:  
if `_isScanning == true && serviceState == Disconnected` → emit `Scanning`.

**2. `AtomicInteger activeScanCount` for concurrent scans**  
`MainViewModel` and `MeshTestViewModel` can both call `scanDevices()` concurrently.  
`_isScanning` must stay `true` until ALL collectors finish.  
`activeScanCount.incrementAndGet()` on entry, `decrementAndGet() == 0` guards the reset.

**3. `foundDevices: ImmutableList<MeshDeviceModel>` in `MainUiState`**  
Replaced the earlier `foundOtherDevicesDuringScan: Boolean`.  
Accumulates across scan restarts via `distinctBy { it.address }`.  
Cleared to `persistentListOf()` on `Connecting` / `Connected`.  
Drives HUD colour and text via `foundDevices.isNotEmpty()`.

**4. Last connected device saved inside `ConnectToMeshDeviceUseCase`**  
`LastConnectedDeviceRepository.save()` is called at the start of every `connect()` call — regardless of whether the connection originated from auto-connect, MeshTest, or any future screen.  
This is the only place that saves, preventing the circular dependency described in Bug 5 below.

**5. Auto-connect flow in `MainViewModel.startAutoConnect()`**  
- Called from `init{}`.
- Cancelled by `observeConnectionStatus` collector when status → `Connecting` / `Connected`.
- `onCompletion { cause }`: restarts scan whenever `cause == null && !autoConnectAttempted` (scan timed out naturally without auto-connect firing). No additional status check needed — `cause != null` already covers cancellation.
- `foundDevices` accumulates across restarts; never cleared mid-scan.

**6. MeshTest Connection tab auto-populates**  
`MeshTestViewModel` observes `connectionStatus`. When `Scanning && scanJob == null` → calls `onScanClick()` internally. When `Connecting || Connected` → cancels `scanJob`. The user sees device list appear without pressing the "Scan" button.

---

## Bug Fixes (discovered during implementation)

### Bug 1 — Android hardware ScanFilter drops device on first scan pass

**Root cause:** Android's hardware `ScanFilter.setServiceUuid()` is evaluated before the scan response packet is received. Meshtastic nodes advertise their service UUID in the scan response (not the primary advertisement). On the first scan pass the hardware filter silently drops the device. On restart Android has the scan response cached → device is found.

**Symptom:** Node never found on first launch; found immediately after restarting scan.

**Fix:** Removed service UUID from `Scanner { filters { match { … } } }` in `KableBleScanner`.  
Software filter applied instead: `advertisement.uuids.any { it == serviceUuid }`.  
Kable's `advertisement.uuids` assembles from both primary advertisement and scan response — immune to the timing issue.  
Address-only hardware filter is kept because address is always present in the primary advertisement.

**File:** [KableBleScanner.kt](mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/ble/KableBleScanner.kt)

---

### Bug 2 — `_isScanning` reset too early with concurrent scans

**Root cause:** `MainViewModel` and `MeshTestViewModel` can both hold an active `scanDevices()` Flow. Whichever finishes first was calling `_isScanning.value = false` even though the other scan was still running. `connectionStatus` dropped out of `Scanning` prematurely.

**Fix:** `activeScanCount: AtomicInteger` in `MeshConnectionRepositoryImpl`.  
`_isScanning = false` is only set in `finally` when `activeScanCount.decrementAndGet() == 0`.

**File:** [MeshConnectionRepositoryImpl.kt](app/src/main/java/ru/tcynik/meshtactics/data/mesh/repository/MeshConnectionRepositoryImpl.kt)

---

### Bug 3 — `onCompletion` race condition with stale `connectionStatus`

**Root cause:** Original `onCompletion` read `_uiState.value.connectionStatus` to decide whether to restart. At the moment `onCompletion` fires, `_isScanning` has already gone `false`, but the `observeConnectionStatus` collector in a separate coroutine hasn't processed the resulting `Disconnected` emission yet. `_uiState.connectionStatus` still held `Scanning` → `is MeshConnectionStatus.Disconnected` check failed → `startAutoConnect()` was never called again.

**Fix:** Removed the stale status check entirely.  
New condition: `cause == null && !autoConnectAttempted`.  
`cause != null` (CancellationException) already covers the Connecting/Connected path.

**File:** [MainViewModel.kt](app/src/main/java/ru/tcynik/meshtactics/presentation/feature/main/MainViewModel.kt)

---

### Bug 4 — Infinite restart loop when no last device is saved

**Root cause:** `foundOtherDevicesDuringScan` was set only when `lastDevice != null && devices.isNotEmpty()`. When no device had ever been saved (`lastDevice == null`), the flag stayed `false` even when nodes were found. `onCompletion` always restarted the scan (correct) but `foundDevices.isNotEmpty()` was never `true` for the HUD.

**Fix:** Simplified to `devices.isNotEmpty()` — any discovered device sets the flag regardless of whether it matches a saved device.

---

### Bug 5 — Circular dependency: `lastDevice` always `null` (root cause of auto-connect never firing)

**Root cause:** `saveLastConnectedDevice` was called only inside `startAutoConnect()` after finding `target != null`. But `target != null` requires `lastDevice != null`. Since manual connections from MeshTest never saved the device, `lastDevice` was always `null` on every launch → auto-connect could never fire.

**Trace from logs:**
```
startAutoConnect: lastDevice=null
autoConnect onEach: devices=[F0:9E:9E:76:76:A1], lastDevice=null, autoConnectAttempted=false
```

**Fix:** Moved `lastConnectedDevice.save(...)` into `ConnectToMeshDeviceUseCase.invoke()` — the single place all connect paths converge. Called before `repository.connect()` on every connection, regardless of source.

**File:** [ConnectToMeshDeviceUseCase.kt](app/src/main/java/ru/tcynik/meshtactics/domain/mesh/usecase/ConnectToMeshDeviceUseCase.kt)

---

## Final State of Affected Files

| File | Change |
|---|---|
| `mesh/.../ble/KableBleScanner.kt` | Software UUID filter; address-only hardware filter |
| `data/.../repository/MeshConnectionRepositoryImpl.kt` | `AtomicInteger activeScanCount`; Scanning state via `_isScanning` |
| `domain/.../usecase/ConnectToMeshDeviceUseCase.kt` | Added `LastConnectedDeviceRepository`; saves device on every connect |
| `presentation/feature/main/MainUiState.kt` | `foundDevices: ImmutableList<MeshDeviceModel>` replaces boolean flag |
| `presentation/feature/main/MainViewModel.kt` | Fixed `onCompletion`; accumulates `foundDevices`; always restarts scan if not auto-connected |
| `presentation/feature/meshtest/MeshTestViewModel.kt` | Auto-starts scan when `Scanning && scanJob == null`; stops on `Connecting/Connected` |
| `di/MeshDataModule.kt` | `ConnectToMeshDeviceUseCase(get(), get())` — second arg is `LastConnectedDeviceRepository` |
| `di/PresentationModule.kt` | Removed `saveLastConnectedDevice` from `MainViewModel` DI |
| `domain/.../repository/LastConnectedDeviceRepository.kt` | New interface |
| `domain/.../usecase/GetLastConnectedDeviceUseCase.kt` | New; synchronous get |
| `domain/.../usecase/SaveLastConnectedDeviceUseCase.kt` | New; suspend save |
| `data/local/mesh/LastConnectedDeviceRepositoryImpl.kt` | New; `multiplatform-settings` persistence |

---

## Device Selection UI — Deliberately NOT on Main Screen

A `NodeSelectorPanel` overlay was built and then removed per product decision.  
Device selection from scan results belongs to a dedicated screen, not the map view.  
Currently handled in MeshTest → Connection tab.  
Future: dedicated radio/connection screen.

---

## Invariants to Preserve

- `saveLastConnectedDevice` must remain inside `ConnectToMeshDeviceUseCase` — not in individual callers.
- `_isScanning` must only be set to `false` when `activeScanCount == 0`.
- `onCompletion` restart check must NOT read `_uiState.connectionStatus` — that value is stale at the point `onCompletion` fires.
- Hardware `ScanFilter` must NOT include service UUID — use software filter on `advertisement.uuids` only.

---

## Change Log

- 2026-04-16: initial plan created (pre-implementation)
- 2026-04-17: rewritten as post-implementation doc; added all 5 bug fixes with root causes, traces, and fixes; updated architecture section; added invariants
