# Plan: Phone GPS to Radio Node

**Date**: 2026-04-06
**Status**: Done — verified working in field test

## Summary

MeshTactics does not send the phone's GPS coordinates to the connected Meshtastic radio node. As a result, the node broadcasts stale cached coordinates from a previous session with the official Meshtastic app. Other nodes see incorrect map markers.

The complete sending pipeline already exists in the mesh module: `AndroidMeshLocationManager` collects phone GPS from `LocationRepository` and calls `CommandSender.sendPosition()` on each update. The pipeline is gated by `UiPrefs.shouldProvideNodeLocation(nodeNum)`, which defaults to `false` and has no UI toggle in MeshTactics. Enabling the feature requires: (1) changing the default to `true`, and (2) adding a user-facing toggle in NodeSettings.

## Research Findings (Phase 0 — complete)

- **Sending infrastructure**: `AndroidMeshLocationManager.start(scope, sendPositionFn)` → `CommandSender.sendPosition(ProtoPosition)` → `PacketHandlerImpl.sendToRadio` → BLE `toRadio` characteristic. Fully implemented.
- **Gate**: `MeshConnectionManagerImpl:114` reads `UiPrefs.shouldProvideNodeLocation(myNodeNum)`. If `false`, `locationManager.start()` is never called.
- **Default**: `UiPrefsImpl:140` — `it[key] ?: false` — off by default.
- **Setter**: `UiPrefs.setShouldProvideNodeLocation(nodeNum, Boolean)` exists and writes to DataStore.
- **Location source**: `LocationRepositoryImpl` — separate from `AppLocationProvider`, uses Android `LocationManager` directly. Independent from the map display layer.
- **Interval**: position is sent on every GPS update from `LocationRepository` (no throttling in `AndroidMeshLocationManager`). Acceptable for MVP.

## Scope

**In scope:**
- Change default to `true` so the radio gets GPS immediately after connection (Phase 1)
- User toggle "Send location to node" in NodeSettings (Phase 2)

**Out of scope:**
- Custom update interval (leave to mesh module defaults)
- Location accuracy / battery optimization
- Sending location to non-connected peer nodes

## Architecture Notes

- No domain layer changes — `UiPrefs` interface already has `shouldProvideNodeLocation` / `setShouldProvideNodeLocation`.
- `MeshConnectionManagerImpl` uses `UiPrefs` (not `MeshPrefs`) — do not confuse the two implementations.
- `NodeSettingsViewModel` must access `UiPrefs` via DI. Verify injection path before Phase 2.
- Phase 1 is a single-line change; Phase 2 is a standard ViewModel + Screen pattern.

## Phase Plan

### Phase 0 — Research ✅ Done
- **Output**: findings documented in this plan (see Research Findings above)

### Phase 1 — Quick Fix (default to `true`)
- **Goal**: radio receives phone GPS immediately after connection, no UI required
- **Tasks**:
  - `UiPrefsImpl.kt:140` — change `?: false` to `?: true`
- **Skill**: direct coding
- **Output**: single-file change; radio starts receiving GPS on next connection

### Phase 2 — UI Toggle in NodeSettings
- **Goal**: user can disable location sharing per node
- **Tasks**:
  1. Verify `UiPrefs` DI injection path into `NodeSettingsViewModel`
  2. Add `provideLocation: Boolean` to `NodeSettingsUiState`
  3. Add `fun onProvideLocationToggled(enabled: Boolean)` in `NodeSettingsViewModel` — calls `uiPrefs.setShouldProvideNodeLocation(nodeNum, enabled)`
  4. Add `SwitchPreference` item "Send location to node" in `NodeSettingsScreen`
- **Skill**: direct coding
- **Output**: working toggle, persisted in DataStore

### Phase 3 — Integration Review
- **Goal**: confirm no Clean Architecture violations
- **Tasks**: review that `UiPrefs` is not called outside ViewModels in presentation layer
- **Skill**: `/architect review: UiPrefsImpl, NodeSettingsViewModel`
- **Output**: review report, violations fixed

### Phase 4 — Skill & Docs Update
- **Goal**: project metadata reflects completed feature
- **Tasks**:
  - Update `CLAUDE.md` feature table
  - Set this plan status to `Done`
  - Update `memory/project_state.md`
- **Skill**: direct edit
- **Output**: accurate documentation

### Phase 5 — Commit
- **Tasks**: stage `UiPrefsImpl.kt` + `NodeSettings*` files, commit via `/commit`
- **Skill**: `/commit`
- **Output**: clean `git status`

## Coordination Map

```
Phase 0: [Research — done]
Phase 1: [direct coding — UiPrefsImpl.kt, 1 line]
Phase 2: [direct coding — NodeSettingsUiState, NodeSettingsViewModel, NodeSettingsScreen]
Phase 3: /architect review: UiPrefsImpl, NodeSettingsViewModel
Phase 4: [docs update — CLAUDE.md, plan file, memory/]
Phase 5: /commit
```

## Open Questions

1. **Phase 1 vs Phase 2 priority**: Is the default-`true` fix sufficient for current testing, or is the UI toggle needed now?
2. **NodeSettingsViewModel DI**: Does `NodeSettingsViewModel` currently have `UiPrefs` injected, or does it need to be added to the constructor?
3. **Throttling**: `AndroidMeshLocationManager` sends on every GPS update. If `LocationRepository` emits frequently, this may spam the BLE channel. Monitor in field testing.

## Debug Session (2026-04-08)

### Симптом
После Phase 1 (`?: true` в `UiPrefsImpl`) нода по-прежнему транслирует всегда одинаковые координаты. Гипотеза: это дефолтная позиция карты (`56.0184, 92.8672`).

### Анализ кода

**DEFAULT_CAMERA_POSITION не попадает в mesh pipeline.** `MainViewModel` использует `MapCameraPosition` только для камеры. Нет ни одного кода-пути, который бы передавал его в `CommandSender`.

**Полный pipeline отправки GPS:**
```
uiPrefs.shouldProvideNodeLocation(myNodeNum) == true
  → MeshConnectionManagerImpl:117 → locationManager.start(scope) { pos → commandSender.sendPosition(pos) }
    → AndroidMeshLocationManager:48 → hasLocationPermission() check
      → locationRepository.getLocations() [callbackFlow, без initial value]
        → LocationRepositoryImpl: Android LocationManager callback → trySend(location)
          → sendPositionFn(ProtoPosition(latitude_i, longitude_i, ...))
            → CommandSenderImpl.sendPosition → Logger.d { "Sending our position/time..." } → BLE
```

### Вероятные причины (по убыванию вероятности)

**P1 — GPS fix отсутствует / callbackFlow не эмитит:**
`LocationRepositoryImpl.getLocations()` использует `callbackFlow` без начального значения. Пока Android не получит GPS fix, ни одна позиция не отправляется. Радио продолжает транслировать кеш из прошлой сессии с официальным приложением. Именно это и видно как "всегда одинаковые координаты".

**P2 — Permission check failure:**
`AndroidMeshLocationManager.start():48` — `if (context.hasLocationPermission())` — если false, `locationFlow` никогда не запускается. Нет лога об этом.

**P3 — `fixed_position=true` на радио:**
`CommandSenderImpl.sendPosition:180` — `if (localConfig.value.position?.fixed_position != true)` — если флаг выставлен, `handleReceivedPosition` пропускается. Прошивка радио при `fixed_position=true` игнорирует входящие position packets (firmware behaviour). Позиция радио никогда не обновится.

**P4 — Race: `locationManager.start()` вызывается повторно, но `locationFlow?.isActive == true` → skip:**
При каждом emit `myNodeInfo` отменяется `locationRequestsJob`, но `locationManager.stop()` НЕ вызывается. `locationFlow` остаётся активным. При следующем вызове `start()` срабатывает early return. Это не баг, но мешает дебагу.

**P5 — `DEFAULT_CAMERA_POSITION` (56.0184, 92.8672):** НЕ попадает в pipeline — исключено.

### План дебага

#### Debug Phase A — Verifying the pipeline fires at all (без изменений кода)

1. **Logcat — ищем существующий лог в `CommandSenderImpl.sendPosition:178`:**
   ```
   Logger.d { "Sending our position/time to=$idNum $pos" }
   ```
   Если этого лога нет — `sendPosition` не вызывается → проблема выше по pipeline.

2. **Logcat — `LocationRepositoryImpl`:**
   ```
   "Starting location updates with ... intervalMs=30000"
   ```
   Если нет — `getLocations()` не был вызван вообще.

#### Debug Phase B — Adding diagnostic logs (если Phase A не дала ответа)

**B1 — `AndroidMeshLocationManager.start()` (файл: `mesh/.../service/AndroidMeshLocationManager.kt`):**
```kotlin
override fun start(scope: CoroutineScope, sendPositionFn: (ProtoPosition) -> Unit) {
    Logger.i { "MeshLocationMgr.start() called, locationFlow.isActive=${locationFlow?.isActive}" }
    this.scope = scope
    if (locationFlow?.isActive == true) {
        Logger.i { "MeshLocationMgr: already running, skipping start" }
        return
    }
    if (context.hasLocationPermission()) {
        Logger.i { "MeshLocationMgr: permission OK, subscribing to GPS" }
        locationFlow = locationRepository.getLocations()
            .onEach { location ->
                Logger.i { "MeshLocationMgr: GPS update lat=${location.latitude} lon=${location.longitude}" }
                sendPositionFn(ProtoPosition(...))
            }
            .launchIn(scope)
    } else {
        Logger.w { "MeshLocationMgr: NO location permission, GPS not started" }
    }
}
```

**B2 — `MeshConnectionManagerImpl.start()` — подтвердить что `start()` вызывается:**
Уже есть логи через `Logger.withTag("MeshConnMgr")` — проверить в Logcat.

**B3 — Проверить `fixed_position` в Logcat:**
В `CommandSenderImpl.sendPosition`:
```kotlin
Logger.d { "sendPosition: fixed_position=${localConfig.value.position?.fixed_position}" }
```

#### Debug Phase C — Если GPS поступает, но радио не обновляет позицию

- **Причина**: `fixed_position=true` на радио.
- **Проверка**: в официальном приложении Meshtastic → Radio Config → Position → убедиться что Fixed Position выключен.
- **Fix в коде**: если нужно — `CommandSenderImpl.setFixedPosition(myNum, Position(0.0, 0.0, 0))` сбрасывает fixed position.

#### Debug Phase D — Если GPS не поступает

- Проверить разрешения приложения в настройках Android (Location → Always allow).
- Проверить что GPS на устройстве включён и есть fix (спутники видны).
- Если эмулятор — задать extended controls → Location.

### Решение по итогам дебага

| Найденная причина | Fix |
|---|---|
| Permission denied | Запросить разрешение / добавить лог предупреждения |
| GPS нет fix (emulator) | Задать mock location в extended controls |
| `fixed_position=true` | Отключить в Radio Config или вызвать `setFixedPosition` с нулями |
| `locationFlow` не запускается (логика) | Убрать early-return guard или добавить `stop()` перед повторным `start()` |

## Research: Архитектурный анализ (2026-04-11)

### Оригинальный API Meshtastic

`MeshService.startProvideLocation()` (строка 290) — официальный публичный API:
```kotlin
override fun startProvideLocation() {
    locationManager.start(serviceScope) { commandSender.sendPosition(it) }
}
```
Оригинальное приложение использует **`sendPosition`** (POSITION_APP пакет), а не `setFixedPosition`.

### Семантика двух методов

| Метод | Механизм | Назначение |
|---|---|---|
| `sendPosition(pos, destNum=null)` | POSITION_APP пакет на `myNum` | Обновить позицию радио; прошивка бродкастит по своему расписанию |
| `setFixedPosition(destNum, pos)` | Admin `set_fixed_position` | Конфигурационная команда: отключает внутренний GPS, ставит статическую позицию |

### Проблема текущей реализации

MeshTactics использует `setFixedPosition` на каждом GPS-обновлении телефона. Это создаёт два следствия:

1. **`fixed_position = true` в прошивке** — внутренний GPS радио отключён. Позиция в конфиге обновляется каждые 30 сек, но радио бродкастит её соседям по своему таймеру (`position_broadcast_secs`, дефолт 15–30 мин). Отсюда — визуально "позиция не меняется" при движении.

2. **Нет подтверждения** — `setFixedPosition` вызывает `sendAdmin(..., wantResponse=false)`. Если Admin-сообщение отклонено прошивкой (неверный passkey, конфликт сессии), приложение об этом не узнаёт.

### Почему `setFixedPosition` был введён (2026-04-08)

Для радио с **внутренним GPS** (например, T-Beam): при активном GPS модуле `sendPosition` обновляет локальную БД, но прошивка игнорирует внешнюю позицию — использует свой GPS. `setFixedPosition` отключает GPS модуль и форсирует позицию с телефона.

### Правильная архитектура

- **Радио без GPS** (Heltec, RAK, большинство): `sendPosition` корректен и достаточен
- **Радио с GPS**: нужно один раз вызвать `remove_fixed_position` при старте (сбросить состояние), затем `sendPosition` регулярно — либо `setFixedPosition` если нужно именно перехватить управление

Универсальный вариант для движущейся ноды: при `shouldProvide=true` — `remove_fixed_position` при старте + `sendPosition` на каждом GPS-обновлении.

### Статус

Текущая реализация (`setFixedPosition`) **работает** для задачи "нода видна на карте" при условии терпимости к задержке обновления. Для реального отображения движения в реальном времени — требует переработки на `sendPosition` + дополнительного изучения частоты бродкаста прошивки при получении внешней позиции.

## Change Log

- 2026-04-06: created
- 2026-04-08: debug session — root cause найдена: `sendPosition` (POSITION_APP mesh packet) не обновляет broadcast-позицию радио при наличии внутреннего GPS. Правильный API: `setFixedPosition` (Admin `set_fixed_position` message). Заменено в `MeshConnectionManagerImpl`. Добавлен `remove_fixed_position` при `shouldProvide=false`.
- 2026-04-08: field test confirmed — дополнительная причина: `fixed_position=true` в официальном приложении Meshtastic блокировал `setFixedPosition` Admin message от MeshTactics. После отключения тоггла нода отобразилась на карте в корректном месте. Фича работает.
- 2026-04-11: research — архитектурный анализ: оригинальный API (`sendPosition`) vs текущая реализация (`setFixedPosition`). Выявлена причина стагнации позиции при движении. Добавлен раздел Research выше.
