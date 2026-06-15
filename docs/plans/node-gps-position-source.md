# Plan: Node GPS Position Source

**Date**: 2026-06-09
**Status**: Planned — реализуется когда понадобится поддержка нод со встроенным GPS

## Summary

Расширение `BackgroundPositionSession` для работы с нодами, имеющими встроенный GPS-чип (T-Beam, T-Deck, RAK с GPS и т.п.). Сейчас сессия всегда работает в режиме «Phone GPS»: телефон — единственный источник координат, непрерывно шлёт `sendPosition` через BLE. Для нод со встроенным GPS это избыточно и может конфликтовать с firmware GPS.

Связанные документы:
- `.claude/docs/background-position-pipeline.md` — описание реализованного пайплайна (Фазы 1–3)
- `.claude/docs/phone-gps-to-radio.md`
- `.claude/plans/background-position-pipeline.md` → `.claude/archive/background-position-pipeline.md`

---

## Проблема

При `gps_mode = ENABLED` (нода с встроенным GPS):
- Нода самостоятельно определяет и транслирует позицию по LoRa
- Телефон не должен слать `sendPosition` — это либо конфликтует с firmware GPS, либо бесполезно
- Screen off телефона **не влияет** на geo в mesh — нода автономна

Сейчас `BackgroundPositionSession` не различает режимы и всегда запускает GPS-мост.

---

## Целевая архитектура

### `PositionSourceMode` enum

```kotlin
enum class PositionSourceMode {
    PHONE_GPS,   // нода без GPS, телефон шлёт sendPosition
    NODE_GPS,    // нода со встроенным GPS, телефон только конфигурирует
    FIXED,       // SOS / фиксированная точка
}
```

### `PositionSource` interface

```kotlin
interface PositionSource {
    val mode: PositionSourceMode
    fun observePosition(): Flow<GeoPosition>
}
```

### Реализации

| Класс | Режим | Поведение |
|---|---|---|
| `PhoneGpsPositionSource` | `PHONE_GPS` | читает `GpsRepository`, выдаёт `Flow<GeoPosition>` |
| `NodeGpsPositionSource` | `NODE_GPS` | читает телеметрию ноды (`NodeRepository`), выдаёт позицию без отправки через BLE |

### Авто-детект режима

При коннекте — читать `localConfig.position.gps_mode`:
- `NOT_PRESENT` или `DISABLED` + телефон предоставляет позицию → `PHONE_GPS`
- `ENABLED` → `NODE_GPS`

Логика детекта — в `BackgroundPositionSession` или отдельном `PositionSourceSelector`.

### Поведение `BackgroundPositionSession` в `NODE_GPS` режиме

- GPS телефона **не запускается** (`gpsLifecycleController` не вызывается)
- `sendPosition` **не отправляется**
- Сессия только: проверяет/пишет `position_broadcast_secs`, `precision`, флаги
- Мониторит `position.location_source` из телеметрии ноды

---

## Scope

**In scope:**
- `PositionSourceMode` enum
- `PositionSource` interface + `PhoneGpsPositionSource` impl
- `NodeGpsPositionSource` impl — читает позицию из `NodeRepository.myNodeInfo.position`
- Авто-детект режима в `BackgroundPositionSession` при старте сессии
- `BackgroundPositionSession`: условный запуск GPS моста по режиму
- UI-индикатор источника позиции в HUD (иконка или лейбл)

**Out of scope:**
- Объединение `GpsService` и `MeshService` в один FGS
- MQTT fallback при `DeviceSleep`
- Store & Forward replay пропущенных позиций

---

## Implementation Phases

### Фаза 1 — PositionSource abstraction

| # | Задача |
|---|---|
| 1.1 | `PositionSourceMode` enum + `PositionSource` interface |
| 1.2 | `PhoneGpsPositionSource` — оборачивает существующий `GpsRepository` flow |
| 1.3 | Рефактор `BackgroundPositionSession` — использует `PositionSource` вместо прямого `locationManager.start()` |

### Фаза 2 — NodeGpsPositionSource

| # | Задача |
|---|---|
| 2.1 | `NodeGpsPositionSource` — читает `NodeRepository.myNodeInfo`, маппит `position` → `GeoPosition` |
| 2.2 | Авто-детект в `BackgroundPositionSession`: читает `localConfig.position.gps_mode` при коннекте |
| 2.3 | Условный запуск GPS телефона: только в `PHONE_GPS` режиме |

### Фаза 3 — UI индикатор

| # | Задача |
|---|---|
| 3.1 | HUD: иконка или лейбл источника позиции (телефон / нода) |
| 3.2 | Раздельные конфиг-профили Phone GPS / Node GPS в `LocationConfigCard` |

---

## Критерии готовности

- [ ] T-Beam (gps_mode=ENABLED): screen off телефона не влияет на обновление позиции у соседей
- [ ] RAK без GPS (gps_mode=NOT_PRESENT): телефон продолжает слать позицию через BLE как раньше
- [ ] Авто-переключение режима при смене `gps_mode` в settings без переподключения
- [ ] Логи показывают корректный режим `PHONE_GPS` / `NODE_GPS` при старте сессии
