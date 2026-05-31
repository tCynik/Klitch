# Channel Sync Validation

**Status**: Done (2026-04-27), updated 2026-05-31

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
2. `showConnectionLabel` → "Сопряжено с ${shortName}" (green)
3. else → empty

## Session Passkey (firmware 2.5+)

**Проблема**: firmware 2.5+ молча дропает любые admin-команды (`set_channel`, `set_config`, `set_owner`), в которых поле `session_passkey` пустое или устаревшее. Никакого error response — команда просто игнорируется, UI показывает применённые изменения (оптимистичный апдейт), но после перезагрузки ноды всё откатывается к предыдущему состоянию.

**Механизм**:
1. Клиент шлёт `begin_edit_settings` с `want_response = true`
2. Firmware генерирует случайный 8-байтный passkey и возвращает его в admin response (`AdminMessage.session_passkey`)
3. Все последующие `set_channel`, `commit_edit_settings` должны нести этот passkey в том же поле
4. `commit_edit_settings` закрывает сессию и сбрасывает passkey на firmware

**Важно**: `get_device_metadata_request` passkey **не возвращает** — источник только `begin_edit_settings` response.

**Реализация** (см. `MeshConfigRepositoryImpl.beginSettingsEdit`):
```
commandSender.setSessionPasskey(EMPTY)         // сброс устаревшего passkey
begin_edit_settings (wantResponse=true)        // firmware вернёт passkey
awaitAdminPacket(TX ACK)
withTimeoutOrNull(3s) { sessionPasskeyFlow.first { it.size > 0 } }
```
После этого каждый `commandSender.sendAdmin(...)` автоматически вставляет текущий `sessionPasskey.value` в поле `AdminMessage.session_passkey`.

**Ловушка**: `handleAdminMessage` вызывается для КАЖДОГО входящего admin-пакета. Wire proto возвращает `ByteString.EMPTY` для отсутствующего `session_passkey`. Нельзя вызывать `setSessionPasskey(EMPTY)` на каждый ответ — это сотрёт только что полученный passkey ещё до того, как уйдут `set_channel` команды.  
Правило: `if (u.session_passkey.size > 0) commandSender.setSessionPasskey(u.session_passkey)`.

**Debug логи** (тег `Contour`):

| Лог | Значение |
|---|---|
| `D handleAdminMessage: session_passkey received size=8` | Firmware вернул passkey |
| `D beginSettingsEdit: session_passkey acquired size=8` | Passkey получен, `set_channel` пойдут с ним |
| `W beginSettingsEdit: passkey timeout, proceeding without` | Firmware **не** вернул passkey (< 2.5 или BLE timeout) |

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
