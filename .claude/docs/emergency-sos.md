# Emergency SOS

**Status**: Done (2026-04-26) — архитектура пересмотрена 2026-05-29
**Branch**: implementing_sos

> Концепция контура Emergency, связь с Primary-механикой и SOS-режим как отдельный концепт
> описаны в `.claude/docs/contours.md` (раздел "SOS-режим и Emergency как Primary").
> Этот документ: broadcast-логика, UI-компоненты, шаблоны сообщений.

## Summary

Emergency SOS — режим тревоги. Активация: отправляется дистресс-сообщение с координатами
на LongFast-слот (резолвится через `ChannelSlotResolver`), запускается непрерывная трансляция
геопозиции Waypoint-ами непосредственно на Emergency (LongFast) контур.
Повторное нажатие отменяет тревогу: останавливает трансляцию, отправляет all-clear.

**Канал ноды не переназначается** — нет перезагрузки ноды ни при активации, ни при отмене.

## Key Files

| Файл | Роль |
|---|---|
| `domain/emergency/repository/EmergencyPositionBroadcastRepository.kt` | Доменный интерфейс (start/stop/isActive) |
| `data/emergency/EmergencyPositionBroadcastRepositoryImpl.kt` | Реализация: loop 30 сек — `setFixedPosition` + Waypoint на LongFast; `stop()` вызывает `removeOwnFixedPosition` |
| `domain/emergency/usecase/TriggerEmergencyUseCase.kt` | Устанавливает флаг, отправляет дистресс-сообщение, запускает broadcast |
| `domain/emergency/usecase/CancelEmergencyUseCase.kt` | Отправляет all-clear, останавливает broadcast (→ removes fixed pos), снимает флаг |
| `domain/emergency/usecase/ObserveEmergencyModeUseCase.kt` | Оборачивает `ContourRepository.observeSosMode()` |
| `data/mesh/GeoSendPolicyImpl.kt` | Блокирует "Provide Location" во время SOS (`observeSosMode().map { !it }`) |
| `domain/mesh/repository/MeshConfigRepository.kt` | `setFixedPosition(lat, lon, alt)` + `removeOwnFixedPosition()` |
| `presentation/feature/settings/UserTabContent.kt` | `EmergencyContourCard`, `TriggerEmergencyDialog`, `CancelEmergencyDialog` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | `onSosClick`, `onTriggerEmergencyConfirm`, `onCancelEmergencyConfirm` |
| `presentation/feature/settings/UserSettingsUiState.kt` | `emergencyMode`, `isNodeConnected`, `showTriggerDialog`, `showCancelDialog`, `emergencyEvent` |
| `di/UserSettingsModule.kt` | DI-привязка всех use cases и репозитория |

## Architecture Decisions

### SOS-режим vs isActive Emergency

SOS-режим (`sos_mode_active` в DataStore) — отдельный флаг, **не тождественен** `isActive` Emergency-контура.
- `isActive` Emergency = всегда `true` (инвариант, см. `contours.md`)
- SOS-режим = управляет: broadcast + флагом видимости

### Без переназначения Primary (ключевое архитектурное решение)

Старая архитектура назначала Emergency-контур (LongFast) в Primary (slot 0) при SOS,
вызывая перезагрузку ноды (~30 сек вне эфира) при активации и ещё одну-две при отмене.

**Текущая архитектура**: канал ноды не трогается.
- Дистресс-сообщение: `channelSlotResolver.hashToSlot[DefaultContour.CHANNEL_HASH] ?: 0`
- GPS broadcast: `sendGeoMark(mark, contourId = DefaultContour.ID)` — `GeoMarkRepositoryImpl` сам резолвит слот через `hashToSlot`

Fallback при отсутствии LongFast в конфиге ноды: slot 0 (Primary).

### Паттерн хранилища с управляемым фоновым заданием
`EmergencyPositionBroadcastRepositoryImpl` держит собственный `CoroutineScope(SupervisorJob() + Dispatchers.IO)` и `Job broadcastJob`. `start()` / `stop()` вызываются только из use cases. В `init` читается персистированный флаг через `ContourRepository.observeSosMode().first()` и при `true` автоматически запускает broadcast (восстановление после перезапуска приложения). DI: `single` с `createdAtStart = true`.

Это **Controllable Background Repository Pattern** — задокументирован в `/architect`.

### SOS flag
Персистируется через `ContourRepository.observeSosMode()` / `setSosMode()` (DataStore key: `sos_mode_active`).
Старый ключ `emergency_is_active` → мигрирует в `sos_mode_active`.

### Отправка дистресс-сообщения
`TriggerEmergencyUseCase` использует `SendChatMessageUseCase` с `contactId = "^all"` и channel index,
резолвленным через `ChannelSlotResolver.hashToSlot[DefaultContour.CHANNEL_HASH]`.

### SOS button state logic
- `!isNodeConnected && !emergencyMode` → кнопка disabled
- `isNodeConnected && !emergencyMode` → красная (FilledIconButton, `error`/`onError`)
- `emergencyMode` → серая (FilledIconButton, `surfaceVariant`/`onSurfaceVariant`)

### Emergency card colors
- Активная тревога: `CardDefaults.cardColors(containerColor = errorContainer)`
- Неактивна: стандартный `CardDefaults.cardColors()`

Токены задокументированы в `/ui-designer` → Established Token Usage.

## Message Templates

- Дистресс: `"{callsign} просит помощи, координаты: {lat}, {lon}"`
- All-clear: `"Пользователь {callsign} отметил, что с ним всё в порядке, помощь не требуется"`
- Callsign fallback: `displayName.ifBlank { localNode.user.shortName }`

## Broadcast

- Интервал: 30 секунд
- Каждая итерация: `setFixedPosition(lat, lon, 0)` → нода транслирует автономно на всех каналах; затем Waypoint `sendGeoMark(..., DefaultContour.ID)` на LongFast-слоте — немедленная видимость
- При `stop()`: `removeOwnFixedPosition()` — нода прекращает автономную трансляцию
- **Если телефон умирает во время SOS**: нода продолжает транслировать последнюю зафиксированную позицию (~каждые 15 мин, firmware-таймер)
- Переживает смену экранов (repository scope, не ViewModel scope)
- Восстанавливается после перезапуска приложения через `createdAtStart = true`; fixed position уже задана в прошивке

### GeoSendPolicy и Provide Location

`GeoSendPolicyImpl.observeAllowed()` возвращает `!sosMode`. Во время SOS:
- "Provide Location" (phone GPS → `sendPosition`) отключается автоматически
- `MeshConnectionManagerImpl` не вызывает `setFixedPosition(0,0,0)` — фиксированная позиция SOS не сбрасывается

## Out of Scope

- Emergency-нотификация на принимающей стороне
- HUD-индикатор активного режима (добавить в план HUD)
- Поведение при отсутствии Emergency-контура (невозможно: `Contour.transport` non-nullable)
- `GeoSendPolicyImpl` всегда возвращает `true` — "Provide Location" не блокируется SOS-состоянием (известный gap, деферред)

## TODO (постоянное решение)

**Force-send + reboot flow** (отдельная задача, требует UI):
1. Принудительно отправить дистресс-сообщение и координаты
2. При подтверждении доставки (или таймауте) — перезагрузить ноду
3. После перезапуска нода автономно транслирует геопозицию на Emergency-канале

Firmware GPS: отдельная фича, запланирована.
