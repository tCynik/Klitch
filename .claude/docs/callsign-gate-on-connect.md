# Callsign Gate on Connect

## Назначение

Блокировка подключения к BLE-узлу, если у пользователя не задан позывной (`displayName` в `AppUser`). Офлайн-функции (карта, метки, чат локально) работают без ограничений.

## Поведение

| Сценарий | Поведение |
|---|---|
| Авто-подключение при старте, позывной пуст | Подключение пропускается (тихо). HUD в `Disconnected` — пусто. |
| Сканирование без позывного | HUD в `Scanning`: «установите позывной» (красный). |
| Экран сети (MeshTest) без позывного | `CallsignGateDialog` показывается автоматически при входе. |
| Connect без позывного | `CallsignGateDialog` с `pendingAddress` / `pendingDeviceName`. |
| Подтверждение диалога | `saveAppUser` → `connectToDevice` (если есть pending) → закрытие диалога. |
| Отказ от синхронизации после подключения | `disconnectFromMesh` — нода не остаётся в эфире со старым позывным (см. channel-sync-validation). |
| UserSettings «Сохранить» с пустым полем | `writeOwner` не вызывается, `displayNameError = true`. |

## Архитектура

Зеркалит flow «требуется синхронизация»:

- **MainViewModel** — `callsignRequired` из `ObserveAppUserUseCase`, HUD INFO в ветке `Scanning`, gate в `startAutoConnect()`.
- **MeshTestViewModel** — `CallsignGateDialogState`, gate в `init` и `onConnectClick()`, handlers `onCallsignInput` / `onCallsignConfirmed` / `onCallsignDismissed`.
- **UserSettingsViewModel** — guard в `onSaveAndReboot()`.

## Ключевые файлы

- `presentation/feature/meshtest/state/models/CallsignGateDialogState.kt`
- `presentation/feature/meshtest/components/CallsignGateDialog.kt`
- `presentation/feature/meshtest/MeshTestViewModel.kt`
- `presentation/feature/main/MainViewModel.kt` — `buildConnectionInfoSlot()`, `startAutoConnect()`
- `presentation/feature/settings/UserSettingsViewModel.kt`

## Тесты

- `MainViewModelCallsignTest`
- `MeshTestViewModelCallsignGateTest`
- `UserSettingsViewModelLeaveDialogTest` — guard на пустой позывной
