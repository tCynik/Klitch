# Channel Sync Validation

**Status**: Done (2026-04-27)

## Summary

On connect and on contour activation, the app compares the node's current channel configuration against active contours. If a mismatch is detected the user sees a dialog offering to write channels + reboot the node. Declining sets a persistent in-memory `syncRequired` flag shown in the HUD Radio info slot as "требуется синхронизация" (red).

## Files

### Domain

| File | Role |
|---|---|
| `domain/channel/usecase/CheckContourSyncUseCase.kt` | Pure compare: reads contours + nodeChannels via `first()`, returns `ContourSyncResult` |
| `domain/channel/model/ContourSyncResult.kt` | `sealed class`: `InSync`, `NeedsSync` |
| `domain/mesh/usecase/RebootNodeUseCase.kt` | Wraps `MeshConfigRepository.rebootNode()` |
| `domain/channel/repository/ContourSyncStateRepository.kt` | In-memory domain state bus: `syncRequired: StateFlow<Boolean>`, `setSyncRequired()`, `clear()` |

### Data

| File | Role |
|---|---|
| `data/mesh/repository/MeshConfigRepositoryImpl.kt` | `rebootNode()` — calls `handleRequestReboot(generatePacketId(), myNodeNum)` |
| `data/channel/repository/ContourSyncStateRepositoryImpl.kt` | `MutableStateFlow<Boolean>` implementation |

### Presentation

| File | Role |
|---|---|
| `presentation/ui/components/SyncRequiredDialog.kt` | `AlertDialog`; stateless composable |
| `presentation/feature/main/MainUiState.kt` | Added `showSyncDialog`, `syncRequired` |
| `presentation/feature/main/MainViewModel.kt` | Checks sync on connect; confirm/dismiss handlers; HUD slot priority |
| `presentation/feature/settings/UserSettingsUiState.kt` | Added `showSyncDialog` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | Checks sync on `onToggleActive(isActive=true)`; confirm/dismiss handlers; blind write removed from `onConnected()` |

## Sync Check Rules

1. **Emergency (slot 0)**: `ContourHash.compute(slot0.name, slot0.psk) == DefaultContour.CHANNEL_HASH`
2. **Active non-emergency contours**: node must have an enabled slot (index ≠ 0) whose hash matches `contour.transport.meshtastic.channelHash`
3. Inactive contours → not checked

## HUD Radio Info Slot Priority (Connected state)

1. `syncRequired == true` → "требуется синхронизация" (red) ← highest priority
2. `!hasChannelOnNode` → "Настройте канал" (red)
3. `showConnectionLabel` → "Сопряжено с \${shortName}" (green)
4. else → empty

## Patterns Used

- `ContourSyncStateRepository` — **In-memory Domain State Bus** (see `/architect`)
- `SyncRequiredDialog` — **stateless AlertDialog** composable (see `/ui-designer`)
- `RebootNodeUseCase` — wraps `MeshConfigRepository.rebootNode()`

## Tests

| Test file | Cases |
|---|---|
| `CheckContourSyncUseCaseTest.kt` | InSync, NeedsSync (slot 0 mismatch, missing active contour, disabled slot) |
| `ContourSyncStateRepositoryImplTest.kt` | set/clear/observe |
| `UserSettingsViewModelSyncDialogTest.kt` | `onToggleActive(true)` → connected → NeedsSync → showSyncDialog |
