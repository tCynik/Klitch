# Background Position Pipeline

## What it does

Единый пайплайн непрерывной передачи позиции телефона в mesh при выключенном экране. Решает проблему обрыва `GPS → BLE → нода → LoRa` моста при `DeviceSleep` в `MeshService`.

## Архитектура (Фазы 1–3)

```
GpsService (FGS: location)          MeshService (FGS: connectedDevice|location)
     │                                       │
GpsRepository (5 с)        BackgroundPositionSession (app-level singleton)
     │                              │                │
     └──── locationManager ─────────┘         MeshWakeLockManager
                │                                    │
          sendPosition()                    PARTIAL_WAKE_LOCK
          broadcastPosition()               (когда useWakeLock=true)
                │
         BleRadioInterface
                │
         CONNECTION_PRIORITY_HIGH (всегда)
                │
         BLE → нода → LoRa
```

## Key classes

### Фаза 1 — Quick win
- **`MeshConnectionManagerImpl`** — `handleDeviceSleep()` больше не вызывает `locationManager.stop()`. `DeviceSleep` — транзиентное состояние, GPS→radio мост продолжает работать.
- **`MeshConnectionManagerImpl`** — при `DeviceSleep → Connected` вызывает `locationManager.flushLastPosition()` для немедленного обновления.
- **`MeshConnectionManagerImpl`** — `sleepEnterTime` + лог `"DeviceSleep duration: Nms"` при reconnect.

### Фаза 2 — BackgroundPositionSession
- **`BackgroundPositionSession`** (`app/data/mesh/`) — координирует GPS lifecycle и отправку позиции. Живёт в scope приложения (Koin singleton, eager init в `MyMeshApplication`). Подписан на `uiPrefs.shouldProvideNodeLocation(nodeNum)` + `geoSendPolicy.observeAllowed()`. При `allowed=true`: вызывает `gpsLifecycleController.start()` + `locationManager.start()` → `sendToAllSlots()`.
- **`sendToAllSlots()`** — шлёт позицию в Primary канал (`commandSender.sendPosition`) + все активные contour-слоты (`commandSender.broadcastPosition`).
- **`OnConnectPositionSender`** — one-shot `sendPosition` при каждом коннекте; дополняет `BackgroundPositionSession`, не заменяет.

### Фаза 3 — BleBackgroundPolicy
- **`BleConnection.requestConnectionPriority(high: Boolean)`** — новый метод в интерфейсе. Реализован в `KableBleConnection` через `AndroidPeripheral.Priority.High`. Вызывается в `BleRadioInterface.onConnected()` — всегда `HIGH`.
- **`BatteryOptimizationHelper.kt`** (`presentation/util/`) — `Context.requestIgnoreBatteryOptimizationIfNeeded()`. Вызывается в `GpsService.onCreate()` (раньше — в `NetworkSettingsScreen` при ручном тоггле, убран при переходе на auto-config). Permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` добавлен в `app/AndroidManifest.xml`.
- **`MeshWakeLockManager`** (`app/data/mesh/`) — `PARTIAL_WAKE_LOCK` acquire/release. Observes `uiPrefs.useWakeLock.combine(geoSendPolicy.observeAllowed())`. Eager init в `MyMeshApplication`.
- **`UiPrefs.useWakeLock`** — DataStore ключ `"use_wake_lock"`, default `false`. Toggle в `LocationConfigCard` секция "Фоновая стабильность".

## Non-obvious decisions

- **`DeviceSleep` ≠ остановка GPS**: `DeviceSleep` — это BLE reconnect цикл внутри `BleRadioInterface`. Нода переходит в light sleep, но `BleRadioInterface` продолжает реконнект. Останавливать GPS на каждый `DeviceSleep` приводило к разрыву моста.
- **`flushLastPosition()` при reconnect**: при коротком `DeviceSleep` следующий `sendPosition` может задержаться до следующего GPS тика (до 5 с). Немедленный flush гарантирует свежую позицию у соседей сразу после reconnect.
- **`CONNECTION_PRIORITY_HIGH` всегда**: MeshTactics всегда работает с позицией и требует минимальной задержки BLE. Нет смысла динамически переключать — всегда `HIGH`.
- **`PARTIAL_WAKE_LOCK` опциональный**: wake lock потребляет батарею. По умолчанию выключен. Пользователь включает в настройках если наблюдает частые BLE-разрывы при screen off.
- **Battery optimization prompt при каждом включении geo**: Android system сам не показывает диалог если уже в whitelist. `isIgnoringBatteryOptimizations()` — check без сохранения состояния.

## Known limitations / planned extensions

- **Node GPS mode** (Фаза 4): для нод со встроенным GPS (`gps_mode=ENABLED`) `BackgroundPositionSession` не должен запускать GPS телефона. Отдельный план: `docs/plans/node-gps-position-source.md`.
- **`POSITION_FRESHNESS_SECONDS` (2 мин)** в `ObserveNodeMarkersUseCase` — при нормальном пайплайне не должен срабатывать. Потенциально можно увеличить до 5 мин после стабилизации.
- **Два FGS** (`GpsService` + `MeshService`) → два notification. Объединение возможно, но не приоритетно.

## Source

Plan: `docs/archive/background-position-pipeline.md`
