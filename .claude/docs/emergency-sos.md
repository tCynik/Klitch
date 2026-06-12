# Emergency SOS

**Status**: Done (2026-04-26) — архитектура пересмотрена 2026-05-29; broadcast и visibility обновлены 2026-06-12
**Branch**: implementing_sos → contours_remake_may

> Концепция контура Emergency, связь с Primary-механикой и SOS-режим как отдельный концепт
> описаны в `.claude/docs/contours.md` (раздел "SOS-режим и Emergency как Primary").
> Этот документ: broadcast-логика, UI-компоненты, шаблоны сообщений.

## Summary

Emergency SOS — режим тревоги. Активация: отправляется дистресс-сообщение с координатами
на LongFast-слот (резолвится через `ChannelSlotResolver`), запускается непрерывная передача
геопозиции ноды через `setFixedPosition`.
Повторное нажатие отменяет тревогу: останавливает broadcast, отправляет all-clear.

**Канал ноды не переназначается** — нет перезагрузки ноды ни при активации, ни при отмене.

## Key Files

| Файл | Роль |
|---|---|
| `domain/emergency/repository/EmergencyPositionBroadcastRepository.kt` | Доменный интерфейс (start/stop/isActive) |
| `data/emergency/EmergencyPositionBroadcastRepositoryImpl.kt` | Реализация: loop 30 сек — только `setFixedPosition`; `stop()` вызывает `removeOwnFixedPosition` |
| `domain/emergency/usecase/TriggerEmergencyUseCase.kt` | Устанавливает флаг, отправляет дистресс-сообщение, запускает broadcast |
| `domain/emergency/usecase/CancelEmergencyUseCase.kt` | Отправляет all-clear, останавливает broadcast (→ removes fixed pos), снимает флаг |
| `domain/emergency/usecase/ObserveEmergencyModeUseCase.kt` | Оборачивает `ContourRepository.observeSosMode()` |
| `data/mesh/GeoSendPolicyImpl.kt` | `observeAllowed() = flowOf(true)` — "Provide Location" не блокируется |
| `domain/mesh/repository/MeshConfigRepository.kt` | `setFixedPosition(lat, lon, alt)` + `removeOwnFixedPosition()` |
| `domain/map/usecase/ObserveNodeMarkersUseCase.kt` | SOS bypass: при `sosMode=true` — все ноды видны |
| `domain/mesh/usecase/ObserveGeoNodesUseCase.kt` | SOS bypass: при `sosMode=true` — все ноды видны |
| `domain/channel/usecase/CheckNodeSyncUseCase.kt` | Пропускает весь sync-check если SOS активен |
| `presentation/feature/main/MainViewModel.kt` | SOS restore dialog: проверяет флаг при старте, `onSosRestoredKeep/Disable` |
| `presentation/feature/main/MainUiState.kt` | `showSosRestoredDialog`, `isSosActive`, `showSosTriggerDialog`, `showSosCancelDialog` |
| `presentation/feature/settings/UserTabContent.kt` | `EmergencyContourCard`, `TriggerEmergencyDialog`, `CancelEmergencyDialog` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | `onSosClick`, `onTriggerEmergencyConfirm`, `onCancelEmergencyConfirm` |
| `di/UserSettingsModule.kt` | DI-привязка use cases и репозитория |

## Architecture Decisions

### SOS-режим vs isActive Emergency

SOS-режим (`sos_mode_active` в DataStore) — отдельный флаг, **не тождественен** `isActive` Emergency-контура.
- `isActive` Emergency = всегда `true` (инвариант, см. `contours.md`)
- SOS-режим = управляет: broadcast + видимостью нод + sync-check

### Без переназначения Primary (ключевое архитектурное решение)

Канал ноды не трогается при SOS:
- Дистресс-сообщение: `channelSlotResolver.hashToSlot[DefaultContour.CHANNEL_HASH] ?: 0`
- GPS broadcast: только `setFixedPosition` (нода транслирует автономно на всех каналах)

Fallback при отсутствии LongFast в конфиге ноды: slot 0 (Primary).

### Broadcast — только setFixedPosition

`EmergencyPositionBroadcastRepositoryImpl` каждые 30 секунд:
1. Читает `gpsRepository.location.value` (текущий GPS телефона)
2. Вызывает `meshConfigRepository.setFixedPosition(lat, lon, 0)`

Нода обновляет зафиксированную позицию и транслирует её автономно на всех каналах по firmware-таймеру.
Waypoint'ы (`sendGeoMark`) **не отправляются** — позиция передаётся только через механизм фиксированной позиции.

Конструктор: `GpsRepository`, `ContourRepository`, `MeshConfigRepository` (без `GeoMarkRepository`).

### Видимость нод в SOS-режиме

При `sosMode = true` фильтр по контурам в `ObserveNodeMarkersUseCase` и `ObserveGeoNodesUseCase`
снимается полностью: все ноды с валидной позицией видны на карте независимо от их канала.

```kotlin
if (sosMode) return@filter true   // bypass contour filter
```

При `sosMode = false` — стандартная фильтрация по слотам и активным контурам.

### Sync-check пропускается при SOS

`CheckNodeSyncUseCase` возвращает `NodeSyncResult.InSync` сразу, если `observeEmergencyMode().first() == true`.
Rationale: в режиме тревоги попытка синхронизации ноды (потенциально с перезагрузкой) недопустима.

### SOS restore при перезапуске приложения

`MainViewModel.init` при старте:
```kotlin
if (observeEmergencyMode().first()) {
    _uiState.update { it.copy(showSosRestoredDialog = true) }
}
```

Диалог "Режим СОС активен. Отключить?" с кнопками:
- **Оставить** / закрытие → `onSosRestoredKeep()` — скрыть диалог, SOS продолжает работать
- **Отключить** → `onSosRestoredDisable()` → скрыть диалог + `CancelEmergencyUseCase`

`EmergencyPositionBroadcastRepositoryImpl.init` независимо восстанавливает broadcast через `createdAtStart = true`.

### Паттерн хранилища с управляемым фоновым заданием

`EmergencyPositionBroadcastRepositoryImpl` держит собственный `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
и `Job broadcastJob`. `start()` / `stop()` вызываются только из use cases. DI: `single` с `createdAtStart = true`.

Это **Controllable Background Repository Pattern** — задокументирован в `/architect`.

### SOS flag

Персистируется через `ContourRepository.observeSosMode()` / `setSosMode()` (DataStore key: `sos_mode_active`).

### GeoSendPolicy

`GeoSendPolicyImpl.observeAllowed() = flowOf(true)` — "Provide Location" не блокируется SOS-состоянием.
Rationale: гео отправляется на slot 0 (Primary); broadcast управляется отдельно через `setFixedPosition`.

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
- Каждая итерация: `setFixedPosition(lat, lon, 0)` — нода обновляет зафиксированную позицию и транслирует автономно
- При `stop()`: `removeOwnFixedPosition()` — нода прекращает автономную трансляцию, возвращается в обычный режим
- **Если телефон умирает во время SOS**: нода продолжает транслировать последнюю зафиксированную позицию (firmware-таймер ~каждые 15 мин)
- Переживает смену экранов (repository scope, не ViewModel scope)
- Восстанавливается после перезапуска приложения через `createdAtStart = true`

## Out of Scope

- Emergency-нотификация на принимающей стороне
- HUD-индикатор активного режима
- Поведение при отсутствии Emergency-контура (невозможно: `Contour.transport` non-nullable)
- `MeshToChatAdapter` silent mode — счётчик непрочитанных и push-уведомление для Emergency вне SOS не блокируются (gate в mesh-библиотеке, отдельная задача)

## TODO

**Force-send + reboot flow** (отдельная задача, требует UI):
1. Принудительно отправить дистресс-сообщение и координаты
2. При подтверждении доставки (или таймауте) — перезагрузить ноду
3. После перезапуска нода автономно транслирует геопозицию на Emergency-канале
