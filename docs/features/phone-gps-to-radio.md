# Phone GPS to Radio

## What it does

Отправляет координаты телефона на подключённую Meshtastic-ноду, чтобы соседи видели актуальную позицию на карте. Работает непрерывно при выключенном экране.

## Key classes

- **`BackgroundPositionSession`** (`app/data/mesh/`) — главный координатор. Singleton, eager init в `MyMeshApplication`. Подписан на `shouldProvideNodeLocation(nodeNum)` + `geoSendPolicy.observeAllowed()`. При `allowed=true`: запускает GPS + `locationManager.start()` → `sendToAllSlots()` на каждый GPS-тик.
- **`sendToAllSlots()`** — `commandSender.sendPosition(pos)` (Primary) + `commandSender.broadcastPosition(pos, slot)` для каждого активного contour-слота.
- **`OnConnectPositionSender`** — one-shot `sendPosition` при каждом коннекте (до первого GPS-тика сессии).
- **`AndroidMeshLocationManager`** — subscribes to `LocationRepository.getLocations()`, вызывает `sendPositionFn`; mesh module.
- **`GpsLifecycleController`** — запускает `GpsService`; вызывается `BackgroundPositionSession` при `allowed=true`.
- **`UiPrefsImpl`** — `shouldProvideNodeLocation(nodeNum)` defaults to `true`; `NetworkSettingsViewModel.onProvideLocationToggle()` для UI-тоггла.

## Non-obvious decisions

- **`setFixedPosition` first, then `sendPosition`**: для нод с внутренним GPS `sendPosition` (POSITION_APP) игнорируется если `fixed_position=true` в firmware. `remove_fixed_position` admin-команда отправляется один раз при старте сессии.
- **`sendPosition` (POSITION_APP), NOT `setFixedPosition` для обновлений**: `setFixedPosition` — admin-команда, задаёт статичную точку и отключает firmware GPS. Для live tracking используется `sendPosition`.
- **`DeviceSleep` не останавливает GPS**: `BackgroundPositionSession` не зависит от `ConnectionState`. При `DeviceSleep → Connected` `locationManager.flushLastPosition()` немедленно шлёт закешированную позицию.
- **`position_broadcast_secs` на ноде**: firmware по умолчанию 900 с — позиция кажется "замёрзшей". Пайплайн не управляет этим напрямую; пользователь настраивает через `LocationConfigCard`.

## Battery & BLE stability (Фаза 3)

- **`CONNECTION_PRIORITY_HIGH`** — запрашивается при каждом BLE-коннекте (`BleRadioInterface.onConnected()`).
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — prompt при первом включении `provideLocationToMesh`.
- **`PARTIAL_WAKE_LOCK`** — опциональный, toggle `UiPrefs.useWakeLock`; управляется `MeshWakeLockManager`.

## Known limitations / planned extensions

- **Node GPS mode**: при `gps_mode=ENABLED` телефон не должен слать `sendPosition` — нода автономна. Отдельный план: `docs/plans/node-gps-position-source.md`.
- `gps_mode=DISABLED` vs `NOT_PRESENT` — при `NOT_PRESENT` нода не имеет GPS-чипа; нужна позиция от телефона. При `DISABLED` — нода имеет чип, но он отключён.

## Source

Plan: `docs/archive/phone-gps-to-radio.md`
See also: `docs/features/background-position-pipeline.md`
