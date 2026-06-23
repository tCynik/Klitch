# Node Provisioning Auto-Config

## What it does
Приложение само настраивает геопозиционные параметры ноды при подключении ("включил, нажал кнопку, забыл") — пользователь не должен разбираться в специфике протокола Meshtastic. `LocationConfigCard` показывает текущее состояние ноды только как информацию, без ручного редактирования.

## Key classes
- `NodeProvisioningUseCase.provisionPositionConfig()` (domain) — при каждом подключении к ноде проверяет, совпадают ли position-настройки с firmware-дефолтами; если да — пишет Klitch-преcет (`gpsMode=DISABLED`, `broadcastIntervalSecs=1800`, `smartBroadcastEnabled=true`, `positionFlags=897`, `provideLocationToMesh=true`, `primaryChannelPositionPrecision=32`). Если уже сконфигурировано — пропускает запись (без лишнего ребута ноды).
- `NodeProvisioningUseCase` — стале `fixedPositionEnabled` снимается безусловно (`removeFixedPosition`), независимо от firmware-default guard.
- `LocationConfigCard` (presentation) — read-only отображение всех секций (provide-to-mesh, gpsMode, fixed position, broadcast interval, smart broadcast, position flags, channel precision). Единственный интерактивный элемент — `useWakeLock` (локальная настройка телефона, не нода).
- `GpsService.onCreate()` — переехавший туда `requestIgnoreBatteryOptimizationIfNeeded()` (раньше был привязан к ручному тогглу `provideLocationToMesh` в `NetworkSettingsScreen`).

## Non-obvious decisions
- **`broadcastIntervalSecs=1800` несмотря на warning `BROADCAST_INTERVAL_HIGH` (>120с)**: это keepalive-анкер через DeviceMetrics, не основной канал доставки позиции — это делает `sendPosition` (см. `phone-gps-to-radio.md`). Warning в `LocationConfigUi.kt` оставлен как информационный (не блокер) по решению автора — убрать можно в любой момент, не убран сейчас намеренно.
- **`useWakeLock` остался editable**: это локальная настройка телефона (PARTIAL_WAKE_LOCK), не параметр протокола Meshtastic — не противоречит принципу "юзер не должен разбираться в протоколе".
- **idempotent guard расширен новыми полями** (`provideLocationToMesh`, `primaryChannelPositionPrecision`) в тот же `isFirmwareDefault` чек, что и старые (broadcast/smart/flags) — гарантирует один write-cycle на свежей ноде, без повторных ребутов на каждый коннект.
- **`fixedPositionEnabled` removal — отдельная безусловная проверка**, не часть `isFirmwareDefault`: блокер может появиться независимо от провижининга (например, нода была сконфигурена через официальный Meshtastic app).

## Known limitations / planned extensions
- Auto-config срабатывает только через `NodeProvisioningUseCase.provision()`, вызываемый из `ConnectionViewModel` при `onNodeConnected()`. Если нода была переконфигурена вручную (например, в официальном приложении) и параметры не совпадают с firmware-дефолтами — auto-config не сработает повторно (намеренно, чтобы не перетирать ручные изменения и не вызывать лишние ребуты).

## Source
Plan: обсуждение в чате (без отдельного `docs/plans/` файла — bounded task).
See also: `docs/features/phone-gps-to-radio.md`, `docs/features/background-position-pipeline.md`
