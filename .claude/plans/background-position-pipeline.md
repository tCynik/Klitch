# Plan: Background Position Pipeline — фоновая geo-сессия

**Date**: 2026-06-05
**Status**: In Progress — Фазы 1–2 завершены, Фаза 3 не начата

## Summary

Системное решение для непрерывной передачи позиции в mesh при выключенном экране телефона.
Ключевая проблема: `GpsService` держит GPS телефона, но мост **GPS → BLE → нода → LoRa**
обрывается при `DeviceSleep` в `MeshService`. Нужен единый фоновый пайплайн позиции с двумя
режимами источника: Phone GPS (нода без приёмника) и Node GPS (встроенный GPS ноды, будущее).

Связанные документы:
- `.claude/docs/gps-background-service.md`
- `.claude/docs/phone-gps-to-radio.md`
- `.claude/docs/gps-pipeline-debug.md`
- `.claude/docs/fix-gps-sending-logic.md`

---

## Симптом (уточнённый сценарий)

1. Оба телефона включены, ноды подключены по BLE. В обоих приложениях видны друг друга.
2. Телефон **А** остаётся активным (экран включён) — видит ноду Б.
3. Телефон **Б** — гасим экран.
4. Через время метка ноды Б на телефоне А **устаревает** (сереет).
5. Периодически, через значительный промежуток, метка **обновляется снова**.

**Вывод:** проблема на стороне **телефона Б (отправка)**, не телефона А (приём).
Телефон А продолжает слушать mesh; телефон Б перестаёт поставлять свежие координаты на ноду.

---

## Диагноз

### Две независимые цепочки

| Цепочка | Сервис | Назначение |
|---|---|---|
| GPS телефона | `GpsService` → `GpsRepository` | Координаты телефона (5 с) |
| GPS → mesh | `MeshService` → BLE → `sendPosition` | Передача координат на ноду и broadcast в LoRa |

`GpsService` работает при выключенном экране. Но позиция соседа на карте телефона А зависит от
того, доходит ли `sendPosition` с телефона Б до его ноды и транслируется ли дальше по LoRa.

### Цепочка при screen off на телефоне Б

```
Экран Б выключен
  → BLE рвётся / нода уходит в light sleep
  → ConnectionState.DeviceSleep
  → locationManager.stop()          ← GPS→radio мост обрывается
  → packetHandler.stopPacketQueue() ← TX-очередь останавливается
  → sendPosition прекращается
  → Нода Б (без GPS): нет свежих координат
  → LoRa шлёт старую позицию или молчит
  → Телефон А: positionTime > 2 мин → isStale

Периодически:
  → BLE reconnect (краткий)
  → sendPosition проходит
  → Метка на А оживает
```

### Ключевые места в коде

**1. `handleDeviceSleep()` останавливает GPS→radio**

`mesh/.../MeshConnectionManagerImpl.kt`:
```kotlin
private fun handleDeviceSleep() {
    serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
    packetHandler.stopPacketQueue()
    locationManager.stop()   // ← обрыв моста
    mqttManager.stop()
}
```

**2. TX-очередь работает только в `Connected`**

`mesh/.../PacketHandlerImpl.kt`:
```kotlin
while (serviceRepository.connectionState.value == ConnectionState.Connected) {
    // send packets...
}
```

**3. UI-порог устаревания — 2 минуты**

`app/.../ObserveNodeMarkersUseCase.kt`:
```kotlin
private const val POSITION_FRESHNESS_SECONDS = 2 * 60
```

При `position_broadcast_secs = 60` и BLE-пробелах маркер сереет быстрее, чем приходит свежая
позиция.

**4. `DeviceSleep` — транзиентное состояние BLE**

`BleRadioInterface` продолжает реконнект в фоне, но `MeshConnectionManagerImpl` уже остановил
`locationManager`. При кратком `Connected` позиция обновляется — отсюда периодические «оживления».

**5. Два FGS с разной lifecycle-политикой**

| Сервис | Тип FGS | Что держит |
|---|---|---|
| `GpsService` | `location` | OS LocationManager |
| `MeshService` | `connectedDevice\|location` | BLE + mesh TX/RX |

GPS и BLE не связаны единой сессией.

**6. Wake lock не используется**

`WAKE_LOCK` есть в манифесте mesh-модуля, но `PARTIAL_WAKE_LOCK` в коде не применяется.

### Текущая фрагментация логики позиции

| Компонент | Роль | Проблема |
|---|---|---|
| `MeshConnectionManagerImpl` | `locationManager.start/stop` по prefs + geo policy | Останавливает при `DeviceSleep` |
| `OnConnectPositionSender` | Одноразовый `sendPosition` при коннекте | Не покрывает фон |
| `GpsBroadcastCoordinator` / `MeshConfigRepository` | `position_broadcast_secs` на ноде | Только конфиг, не поток координат |
| `GpsService` | GPS телефона | Не связан с mesh TX lifecycle |

---

## Целевая архитектура

### Три слоя

```
┌─────────────────────────────────────────────────────────┐
│  Слой 3: BleBackgroundPolicy                            │
│  power saving off, BLE priority, battery exemption      │
├─────────────────────────────────────────────────────────┤
│  Слой 2: BackgroundPositionSession (MeshService scope)  │
│  PositionBroadcaster + NodeConfigWriter + reconnect     │
├─────────────────────────────────────────────────────────┤
│  Слой 1: PositionSource                                 │
│  Phone GPS | Node GPS (future) | Fixed (SOS)            │
└─────────────────────────────────────────────────────────┘
```

### Слой 1 — `PositionSource` (абстракция источника)

```kotlin
interface PositionSource {
    val mode: PositionSourceMode  // PHONE_GPS | NODE_GPS | FIXED
    fun observePosition(): Flow<GeoPosition>
}
```

| Режим | Источник | Роль телефона |
|---|---|---|
| **Phone GPS** (сейчас, нода без приёмника) | `GpsRepository` (5 с) | Поставщик координат через BLE |
| **Node GPS** (будущее, T-Beam и т.п.) | Встроенный GPS ноды | Только конфигурация + мониторинг |
| **Fixed** (SOS, ручная точка) | Заданные координаты | Запись `setFixedPosition` |

Выбор режима — по `gps_mode` из `localConfig.position` при коннекте:
- `NOT_PRESENT` / `DISABLED` + phone provides → `PHONE_GPS`
- `ENABLED` + встроенный GPS → `NODE_GPS`

### Слой 2 — `BackgroundPositionSession`

**Один координатор** в scope `MeshService`, заменяет:
- `locationManager.start/stop` в `MeshConnectionManagerImpl`
- `OnConnectPositionSender`
- логику broadcast-конфига при коннекте

**Поведение:**

1. Живёт в **scope `MeshService`**, не в `ViewModelScope` / `MainActivity`.
2. Подписан на `PositionSource.observePosition()`.
3. В режиме `PHONE_GPS`: на каждое GPS-обновление → `commandSender.sendPosition()`.
4. **Не останавливается при `DeviceSleep`** — только при `Disconnected`.
5. При `DeviceSleep → Connected`: немедленный flush последней кэшированной позиции.
6. Параллельно пишет на ноду: `position_broadcast_secs=60`, `smart_broadcast=false`,
   `position_precision` по контуру.
7. `GpsLifecycleController.start()` вызывается при старте сессии (GPS поднимается вместе с mesh).

**Ключевая правка:** `handleDeviceSleep()` **не вызывает** `locationManager.stop()`.
`DeviceSleep` — транзиентное состояние BLE (реконнект идёт), не сигнал «прекратить geo».

### Слой 3 — `BleBackgroundPolicy`

Пока активна фоновая geo-сессия:

| Мера | Зачем |
|---|---|
| `is_power_saving = false` на ноде (или `ls_secs` ↑) | Нода не рвёт BLE при screen off |
| BLE `CONNECTION_PRIORITY_HIGH` | Меньше отвалов в Doze |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Samsung/Xiaomi не душат `MeshService` |
| Опционально `PARTIAL_WAKE_LOCK` | Стабильный BLE-мост (toggle в настройках) |

### Целевая модель сервисов

```
MeshService (FGS: connectedDevice|location)
├── BleBackgroundPolicy
├── BackgroundPositionSession
│   ├── PositionSource (Phone / Node / Fixed)
│   ├── PositionBroadcaster (sendPosition + contour slots)
│   └── NodeConfigWriter (broadcast_secs, gps_mode, power)
└── GpsLifecycleController.start()
```

`GpsService` можно оставить как обёртку, но **lifecycle GPS привязать к mesh-сессии**.

---

## Поведение по режимам

### Нода без GPS (текущий кейс)

- Телефон — единственный источник координат (`LOC_EXTERNAL`).
- **Критично:** непрерывный BLE + `sendPosition` каждые 5 с.
- Fallback: периодический `setFixedPosition` как снапшот — нода сохранит последние координаты
  и будет LoRa-broadcast в короткие BLE-пробелы (timestamp обновится только при reconnect).
- `gps_mode` на ноде: `DISABLED` (телефон — источник).

### Нода со встроенным GPS (будущее)

- `gps_mode = ENABLED`, телефон **не** шлёт `sendPosition`.
- `BackgroundPositionSession` только:
  - проверяет `position_broadcast_secs`, precision, flags;
  - мониторит `position.location_source` из телеметрии ноды.
- Screen off на телефоне **не влияет** на geo в mesh — нода автономна по LoRa.
- BLE может спать без потери актуальности позиции на карте соседей.

---

## Scope

**In scope:**
- ~~Убрать `locationManager.stop()` из `handleDeviceSleep()`~~ ✅ Done
- ~~Auto-flush последней GPS-позиции при `DeviceSleep → Connected`~~ ✅ Done
- `BackgroundPositionSession` в scope `MeshService`
- ~~`BleBackgroundPolicy`: `is_power_saving=false` при активном geo-broadcast~~ — **Решено: не делаем** (см. 1.3)
- Привязка `GpsLifecycleController.start()` к mesh-сессии
- `PositionSource` interface + `PhoneGpsPositionSource` implementation
- Battery optimization prompt (один раз, при первом включении geo)
- Документация: `.claude/docs/background-position-pipeline.md` (после реализации)

**Out of scope (отдельные задачи):**
- `NodeGpsPositionSource` (режим встроенного GPS ноды) — Фаза 4
- Объединение `GpsService` и `MeshService` в один FGS — опционально, Фаза 3+
- Ослабление `POSITION_FRESHNESS_SECONDS` в UI — после стабилизации пайплайна
- MQTT fallback при `DeviceSleep`
- Store & Forward replay пропущенных позиций

---

## Implementation Phases

### Фаза 1 — Quick win ✅ Завершена

| # | Задача | Файл | Статус |
|---|---|---|---|
| 1.1 | Убрать `locationManager.stop()` из `handleDeviceSleep()` | `MeshConnectionManagerImpl.kt` | ✅ Done |
| 1.2 | Flush последней позиции при reconnect (`DeviceSleep → Connected`) | `MeshConnectionManagerImpl.kt` + `AndroidMeshLocationManager.kt` | ✅ Done |
| 1.3 | ~~Записывать `is_power_saving=false` при включённом geo-broadcast~~ | — | ❌ Решение: не делаем. `is_power_saving` ортогонален app-driven broadcast; gap 180 с покрывается через `flushLastPosition()` при reconnect. Нода остаётся с настройками по умолчанию. |
| 1.4 | Логирование: `ConnectionState` + `sendPosition` timestamps при screen off | `MeshConnectionManagerImpl`, `AndroidMeshLocationManager` | ✅ Done (`MT/SmartPos`, `MT/PhoneGPS→radio`, `MeshConnMgr`) |

**Критерий:** телефон Б, screen off 10 мин → телефон А видит `positionTime` < 2 мин.

### Фаза 2 — BackgroundPositionSession ✅ Завершена

| # | Задача | Статус |
|---|---|---|
| 2.1 | ~~`PositionSource` interface + `PhoneGpsPositionSource`~~ | — Отложено на Фазу 4: преждевременная абстракция без текущей пользы |
| 2.2 | `BackgroundPositionSession` в scope `MeshService` | ✅ Done (`data/mesh/BackgroundPositionSession.kt`) |
| 2.3 | Перенести логику из `MeshConnectionManagerImpl.locationManager` блока | ✅ Done — блок удалён, `uiPrefs`/`geoSendPolicy` убраны из конструктора |
| 2.4 | Дополнить `OnConnectPositionSender` | ✅ Done — `OnConnectPositionSender` оставлен (one-shot на коннект); `BackgroundPositionSession` добавляет непрерывный broadcast по всем contour-слотам |
| 2.5 | Привязать `GpsLifecycleController.start()` к старту сессии | ✅ Done — вызывается в `BackgroundPositionSession` при `allowed=true` |
| 2.6 | Unit-тесты | ✅ Done — 5 тестов в `BackgroundPositionSessionTest.kt` |

### Фаза 3 — BleBackgroundPolicy (2–3 дня)

| # | Задача |
|---|---|
| 3.1 | BLE `CONNECTION_PRIORITY_HIGH` при активной geo-сессии |
| 3.2 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt |
| 3.3 | Опциональный `PARTIAL_WAKE_LOCK` + toggle в настройках |
| 3.4 | Мониторинг: логировать uptime BLE при screen off |

### Фаза 4 — Node GPS mode (когда понадобится)

| # | Задача |
|---|---|
| 4.1 | `NodeGpsPositionSource` — читает позицию из телеметрии ноды |
| 4.2 | Авто-детект режима по `gps_mode` / `NOT_PRESENT` |
| 4.3 | Раздельные конфиг-профили Phone GPS / Node GPS |
| 4.4 | UI-индикатор источника позиции в HUD |

---

## Ключевые файлы

| Файл | Роль сейчас | Изменение |
|---|---|---|
| `mesh/.../MeshConnectionManagerImpl.kt` | start/stop locationManager, handleDeviceSleep | Убрать stop из DeviceSleep; flush на reconnect |
| `mesh/.../AndroidMeshLocationManager.kt` | GPS → sendPositionFn | Без изменений или делегат в Session |
| `mesh/.../PacketHandlerImpl.kt` | TX-очередь только в Connected | Возможно: priority lane для position packets |
| `mesh/.../BleRadioInterface.kt` | BLE reconnect loop | CONNECTION_PRIORITY_HIGH |
| `app/.../GpsRepositoryImpl.kt` | GPS 5 с | Lifecycle привязка к mesh-сессии |
| `app/.../MeshLocationRepositoryAdapter.kt` | GpsRepository → mesh LocationRepository | Без изменений |
| `app/.../OnConnectPositionSender.kt` | Одноразовый send при коннекте | Заменить BackgroundPositionSession |
| `app/.../MeshConfigRepositoryImpl.kt` | broadcast_secs, gps_mode | + is_power_saving=false |
| `app/.../ObserveNodeMarkersUseCase.kt` | UI stale threshold 2 мин | Out of scope (Фаза 1+) |

---

## Критерии готовности

- [ ] Телефон Б, screen off 30+ мин → телефон А видит позицию Б с `positionTime` < 2 мин
- [ ] HUD телефона Б: `DeviceSleep` не длится > нескольких секунд (или geo работает сквозь него)
- [ ] Нода без GPS: позиция обновляется каждые ≤ 60 с в mesh
- [ ] Нода с GPS (будущее): позиция автономна, screen off телефона не влияет
- [ ] Логи `MT/GpsBroadcast` / `PhoneGPS→radio` подтверждают непрерывный поток при screen off

---

## Риски и trade-offs

| Риск | Митигация |
|---|---|
| Расход батареи телефона Б (BLE + GPS + wake lock) | Toggle wake lock в настройках |
| ~~Расход батареи ноды Б (light sleep off)~~ | — Не актуально: `is_power_saving` не трогаем |
| Регрессия: DeviceSleep раньше предотвращал hot loop TX | TX-очередь по-прежнему stop при DeviceSleep; position идёт через отдельный канал |
| Два FGS → два notification | Объединение в Фазе 3+ (out of scope сейчас) |

---

## Диагностика (для верификации Фазы 1)

Перед и после правок — логи при screen off на телефоне Б:

```
MT/MeshConnMgr  — ConnectionState transitions (Connected/DeviceSleep/Disconnected)
PhoneGPS→radio  — sendPosition timestamps (должны идти каждые ~5 с)
MT/Node         — positionTime соседа на телефоне А
BLE             — reconnect events, uptime
```

Ожидаемый результат после Фазы 1:
- `PhoneGPS→radio` не прерывается при `DeviceSleep`
- `DeviceSleep` кратковременен (< 5 с) или geo идёт сквозь него
- `positionTime` на телефоне А обновляется каждые ≤ 60 с

---

## Версия документа

Создан по итогам анализа сессии 2026-06-05. Сценарий воспроизведения: два телефона,
нода Б — screen off, телефон А наблюдает устаревание метки ноды Б.
