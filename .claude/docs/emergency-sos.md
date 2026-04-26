# Emergency SOS

**Status**: Done (2026-04-26)
**Branch**: implementing_sos

## Summary

Emergency SOS — глобальный флаг тревоги. На экране настроек контур Emergency получает кнопку SOS вместо чекбокса. Нажатие запрашивает подтверждение, отправляет дистресс-сообщение с координатами и запускает непрерывную трансляцию геопозиции в Emergency-канал. Повторное нажатие отменяет тревогу.

## Key Files

| Файл | Роль |
|---|---|
| `domain/emergency/repository/EmergencyPositionBroadcastRepository.kt` | Доменный интерфейс (start/stop/isActive) |
| `data/emergency/EmergencyPositionBroadcastRepositoryImpl.kt` | Реализация: `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, loop с `delay(30s)` |
| `domain/emergency/usecase/TriggerEmergencyUseCase.kt` | Устанавливает флаг, отправляет дистресс-сообщение, запускает broadcast |
| `domain/emergency/usecase/CancelEmergencyUseCase.kt` | Отправляет all-clear, останавливает broadcast, снимает флаг |
| `domain/emergency/usecase/ObserveEmergencyModeUseCase.kt` | Оборачивает `ContourRepository.observeEmergencyIsActive()` |
| `presentation/feature/settings/UserTabContent.kt` | `EmergencyContourCard`, `TriggerEmergencyDialog`, `CancelEmergencyDialog` |
| `presentation/feature/settings/UserSettingsViewModel.kt` | `onSosClick`, `onTriggerEmergencyConfirm`, `onCancelEmergencyConfirm` |
| `presentation/feature/settings/UserSettingsUiState.kt` | `emergencyMode`, `isNodeConnected`, `showTriggerDialog`, `showCancelDialog`, `emergencyEvent` |
| `di/UserSettingsModule.kt` | DI-привязка всех use cases и репозитория |

## Architecture Decisions

### Паттерн хранилища с управляемым фоновым заданием
`EmergencyPositionBroadcastRepositoryImpl` держит собственный `CoroutineScope(SupervisorJob() + Dispatchers.IO)` и `Job broadcastJob`. `start()` / `stop()` вызываются только из use cases. В `init` читается персистированный флаг через `ContourRepository.observeEmergencyIsActive().first()` и при `true` автоматически запускает broadcast (восстановление после перезапуска приложения). DI: `single` с `createdAtStart = true`.

Это **Controllable Background Repository Pattern** — задокументирован в `/architect`.

### emergencyMode flag
Персистируется через `ContourRepository.observeEmergencyIsActive()` / `setEmergencyActive()` (DataStore). Нового хранилища не нужно.

### Отправка дистресс-сообщения
`TriggerEmergencyUseCase` использует `SendChatMessageUseCase` с `contactId = "^all"` и channel index, разрешённым через `ChannelSlotResolver.hashToSlot`.

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
- Механизм: `geoMarkRepository.sendGeoMark(GeoMarkModel(...))` в loop
- Переживает смену экранов (repository scope, не ViewModel scope)
- Восстанавливается после перезапуска приложения через `createdAtStart = true`

## Out of Scope

- Emergency-нотификация на принимающей стороне
- HUD-индикатор активного режима (добавить в план HUD)
- Поведение при отсутствии Emergency-контура (невозможно: `Contour.transport` non-nullable)
