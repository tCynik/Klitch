# Channel Sync Validation

**Status**: Done (2026-04-27), updated 2026-05-13

## Summary

On connect and on contour activation, the app compares the node's current configuration against active contours and the user's callsign. If a mismatch is detected the user sees a dialog offering to write channels + owner name + reboot the node. Declining sets `syncRequired` and **disconnects from the node** so the mesh is not left with the old owner name while the app has a new callsign. On next connect the dialog appears again.

## Files

### Domain

| File | Role |
|---|---|
| `domain/channel/usecase/CheckNodeSyncUseCase.kt` | Pure compare: reads contours + nodeChannels + appUser + deviceConfig, returns `NodeSyncResult` |
| `domain/channel/model/NodeSyncResult.kt` | `sealed interface`: `InSync`, `NeedsSync` |
| `domain/channel/usecase/SyncContoursOnConnectUseCase.kt` | Writes channels (Emergency + active contours) and owner name to node |
| `domain/mesh/usecase/RebootNodeUseCase.kt` | Wraps `MeshConfigRepository.rebootNode()` |
| `domain/channel/repository/ContourSyncStateRepository.kt` | In-memory domain state bus: `syncRequired: StateFlow<Boolean>`, `setSyncRequired()`, `clear()` |

### Data

| File | Role |
|---|---|
| `data/mesh/repository/MeshConfigRepositoryImpl.kt` | `rebootNode()`, `writeChannel()` (sets `position_precision = 32`), `writeOwner()` |
| `data/channel/repository/ContourSyncStateRepositoryImpl.kt` | `MutableStateFlow<Boolean>` implementation |

### Presentation

| File | Role |
|---|---|
| `presentation/ui/components/SyncRequiredDialog.kt` | `AlertDialog`; stateless composable |
| `presentation/feature/main/MainUiState.kt` | `showSyncDialog`, `syncRequired` |
| `presentation/feature/main/MainViewModel.kt` | Runs `CheckNodeSyncUseCase` on connect; sets `syncRequired=true` on NeedsSync; HUD slot priority |
| `presentation/feature/settings/UserSettingsUiState.kt` | `showSyncDialog` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | Checks sync on `onToggleActive(isActive=true)`; confirm/dismiss handlers |
| `presentation/feature/meshtest/MeshTestViewModel.kt` | Checks sync on connect; confirm/dismiss handlers; holds reboot UI state until disconnect+reconnect cycle completes |
| `presentation/feature/meshtest/components/MeshStatusBar.kt` | Shows reboot label in MeshTest status bar |

## Sync Check Rules (`CheckNodeSyncUseCase`)

1. **Emergency (slot 0)**: `ContourHash.compute(slot0.name, slot0.psk) == DefaultContour.CHANNEL_HASH`
2. **Active non-emergency contours**: node must have an enabled slot (index ≠ 0) with matching hash AND `positionPrecision > 0`
3. **Owner name**: if `appUser.displayName.isNotBlank()` and `deviceConfig.longName != appUser.displayName` → NeedsSync
4. **Empty nodeChannels** → InSync (data not yet arrived, skip check)
5. Inactive contours → not checked

## Sync Write Rules (`SyncContoursOnConnectUseCase`)

On user confirmation:
1. Write Emergency to slot 0 if not already matching
2. Write each active non-emergency contour to a free/mismatched slot (`checkPrecision = true` — rewrites slot if `positionPrecision == 0`)
3. Write owner name if `appUser.displayName != node.longName`
4. Caller (`MeshTestViewModel`, `UserSettingsViewModel`) triggers `rebootNode()` after

## NodeProvisioningUseCase (silent, no confirmation)

Runs automatically on every connect from `MainViewModel`. Only writes channels — does NOT write owner name (that would trigger auto-reboot without user confirmation). Uses `checkPrecision = false` → skips precision-only rewrites.

## PKC Key Regeneration on Sync Confirm

Если при подключении был обнаружен сломанный PKC ключ (`CheckOwnPkcHealthUseCase` → `isOwnPkcKeyBroken=true`), флаг `needsPkcRegen` сохраняется в `UserSettingsViewModel`. При подтверждении sync dialog — перед reboot отправляется `regeneratePkcKeys()` (`set_config(SecurityConfig(private_key=EMPTY))`). Один reboot применяет каналы + новые ключи одновременно. Firmware генерирует новую пару и рассылает обновлённый `User(public_key)` соседям.

## MeshTest Reboot Status Contract

On sync dialog confirmation in `MeshTestScreen`:
1. `setRebooting(true)` is set immediately before sync write + reboot call
2. Mesh status bar switches immediately to reboot UI state
3. Reboot label format: `{nodeName} - Перезагрузка...` (fallback: `Перезагрузка...` if name unavailable)
4. Reboot state is cleared only after reboot cycle is observed (`disconnect`/non-connected phase, then `connected` again)

This avoids brief status flicker and prevents premature reboot-state reset that can break reconnect flow.

## HUD Radio Info Slot Priority (Connected state)

1. `syncRequired == true` → "требуется синхронизация" (red) ← highest priority
2. `!hasChannelOnNode` → "Настройте канал" (red)
3. `showConnectionLabel` → "Сопряжено с ${shortName}" (green)
4. else → empty

## Patterns Used

- `ContourSyncStateRepository` — **In-memory Domain State Bus** (see `/architect`)
- `SyncRequiredDialog` — **stateless AlertDialog** composable (see `/ui-designer`)
- `RebootNodeUseCase` — wraps `MeshConfigRepository.rebootNode()`

## Tests

| Test file | Cases |
|---|---|
| `CheckNodeSyncUseCaseTest.kt` | InSync/NeedsSync: slot 0 mismatch, missing contour, disabled slot, position_precision=0, owner name mismatch/match |
| `SyncContoursOnConnectUseCaseTest.kt` | Emergency write, AlreadySynced/FreeSlot/NoFreeSlot for contours |
| `ContourSyncStateRepositoryImplTest.kt` | set/clear/observe |
| `UserSettingsViewModelSyncDialogTest.kt` | `onToggleActive(true)` → connected → NeedsSync → showSyncDialog; dismiss → disconnect |
| `MeshTestViewModelCallsignGateTest.kt` | `onDismissChannelSync` → disconnect |
