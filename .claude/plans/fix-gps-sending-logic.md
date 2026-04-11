# Fix: GPS Sending Logic + Location Settings in ConfigTab

**Date**: 2026-04-11
**Status**: Ready for implementation

---

## Реальный источник проблемы (после полевого теста)

Установлено **два независимых источника**:

1. **Главный:** Тоггл "Provide location to mesh" был выключен. Нет UI для этого параметра в ConfigTab → пользователь не видел/не мог включить.
2. **Архитектурный:** `MeshConnectionManagerImpl` использует `setFixedPosition` (Admin = статическая конфигурация) вместо `sendPosition` (POSITION_APP = live пакет). Нода бродкастила позицию только по таймеру `position_broadcast_secs` (900 сек по умолчанию).

---

## Все факторы, влияющие на обмен геолокацией

### Сторона приложения (Phone → Node)

| # | Фактор | Источник | Дефолт |
|---|---|---|---|
| 1 | **Provide location to mesh** — главный выключатель, включает GPS-пайплайн | `UiPrefs.shouldProvideNodeLocation(nodeNum)` DataStore | `true` (но был выключен!) |
| 2 | **Location permission** — `ACCESS_FINE_LOCATION` должен быть выдан | `context.hasLocationPermission()` в `AndroidMeshLocationManager` | — |
| 3 | **Метод отправки** — `sendPosition` (POSITION_APP) vs `setFixedPosition` (Admin) | `MeshConnectionManagerImpl`, строки 117–135 | ❌ сейчас `setFixedPosition` |
| 4 | **GPS interval** — частота обновления GPS телефона | `LocationRepositoryImpl.DEFAULT_INTERVAL_MS = 30_000L` | 30 сек (хардкод) |

### Сторона прошивки ноды (Node internals + Node → Mesh)

| # | Фактор | Proto field | Дефолт |
|---|---|---|---|
| 5 | **gps_mode** — источник позиции: DISABLED/ENABLED/NOT_PRESENT | `Config.PositionConfig.gps_mode` | `DISABLED` |
| 6 | **fixed_position** — статические координаты, блокируют всё | `Config.PositionConfig.fixed_position` | `false` |
| 7 | **position_broadcast_secs** — как часто нода бродкастит позицию в меш | `Config.PositionConfig.position_broadcast_secs` | **900** (15 мин!) |
| 8 | **position_broadcast_smart_enabled** — адаптивный бродкаст при движении | `Config.PositionConfig.position_broadcast_smart_enabled` | `false` |
| 9 | **position_flags** — что включать в пакет позиции | `Config.PositionConfig.position_flags` (bitmask) | `0` |
| 10 | **broadcast_smart_minimum_distance** — мин. расстояние для smart-бродкаста | `Config.PositionConfig.broadcast_smart_minimum_distance` | — |
| 11 | **gps_update_interval** — как часто нода опрашивает GPS чип (только при gps_mode=ENABLED) | `Config.PositionConfig.gps_update_interval` | 300 сек |

### Сторона канала (Channel → Mesh delivery)

| # | Фактор | Proto field | Дефолт |
|---|---|---|---|
| 12 | **position_precision** — точность позиции при отправке по каналу; **0 = позиция не передаётся** | `Channel.settings.module_settings.position_precision` | 32 (primary), но может быть 0! |

### Приоритеты источников позиции в прошивке

```
fixed_position=true    → всё игнорируется, только статика (НАИВЫСШИЙ)
gps_mode=ENABLED       → GPS чип перезаписывает phone-coordinates на каждом фиксе
gps_mode=DISABLED/NOT_PRESENT + sendPosition → phone-coordinates — единственный источник
```

### Финальный стопор перед выходом в меш

```
position_precision=0 на всех каналах → позиция не бродкастится соседям (независимо от всего выше)
```

> Нода может иметь корректную позицию внутри, но не отправлять её в сеть, если на primary channel `position_precision = 0`.

### Флаги position_flags (bitmask, `Config.PositionFlags`)

| Флаг | Значение | Нужен для |
|---|---|---|
| `ALTITUDE` | 1 | Высота (HAE) |
| `ALTITUDE_MSL` | 2 | Высота над уровнем моря |
| `SATINVIEW` | 32 | Кол-во спутников |
| `SEQ_NO` | 64 | Порядковый номер пакета |
| `TIMESTAMP` | 128 | Временная метка |
| `HEADING` | 256 | Направление движения — **критично для directional_nodes_marks** |
| `SPEED` | 512 | Скорость — **критично для directional_nodes_marks** |

---

## Scope

**Phase 1 — Bug fix: sendPosition вместо setFixedPosition**
- `MeshConnectionManagerImpl.kt` — 1 файл, ~15 строк
- `remove_fixed_position` вызвать один раз при старте sharing

**Phase 2 — Settings UI в ConfigTab**
- Новая карточка `LocationConfigCard` в `ConfigTab`
- Новая `data class LocationConfigUi` в `ConfigTabState`
- Новые use cases: `ObserveLocationConfigUseCase`, `SetProvideLocationUseCase`, `WritePositionConfigUseCase`
- Расширение `MeshTestViewModel` новыми действиями

---

## Phase 1 — sendPosition вместо setFixedPosition

### Файл
[MeshConnectionManagerImpl.kt](mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/data/manager/MeshConnectionManagerImpl.kt)

### Изменение (строки 117–135)

```kotlin
// БЫЛО:
if (shouldProvide) {
    locationManager.start(scope) { pos ->
        commandSender.setFixedPosition(nodeNum, Position(lat, lon, alt))
    }
} else {
    locationManager.stop()
    commandSender.setFixedPosition(nodeNum, Position(0.0, 0.0, 0))
}

// СТАЛО:
if (shouldProvide) {
    // Сбрасываем fixed_position — иначе прошивка игнорирует sendPosition
    commandSender.setFixedPosition(myNodeEntity.myNodeNum, Position(0.0, 0.0, 0))
    locationManager.start(scope) { pos ->
        Logger.i { "PhoneGPS→radio: sendPosition lat=... lon=..." }
        commandSender.sendPosition(pos)
    }
} else {
    locationManager.stop()
    // fixed_position не трогаем — нода использует свой GPS если есть
}
```

---

## Phase 2 — Settings UI в ConfigTab

### Карточки для добавления

Добавить новую карточку **LocationConfigCard** в `ConfigTab` после `DeviceConfigCard`.

#### Секция A: Phone → Node

| Параметр | UI | Источник записи | Источник чтения |
|---|---|---|---|
| Provide location to mesh | `Switch` | `UiPrefs.setShouldProvideNodeLocation(nodeNum, v)` | `UiPrefs.shouldProvideNodeLocation(nodeNum)` |
| Location permission status | `Text` (read-only, цветной индикатор) | — | `context.hasLocationPermission()` |

#### Секция B: Node Position Source

| Параметр | UI | Proto field | Запись |
|---|---|---|---|
| GPS mode | `Dropdown` (DISABLED / ENABLED / NOT_PRESENT) | `gps_mode` | `setConfig(Config(position = cfg.copy(gps_mode = v)))` |
| Fixed position | `Text` (статус) + кнопка "Remove" | `fixed_position` | `commandSender.setFixedPosition(nodeNum, Position(0,0,0))` |

#### Секция C: Node → Mesh Broadcast

| Параметр | UI | Proto field | Запись |
|---|---|---|---|
| Broadcast interval (сек) | `OutlinedTextField` (число) | `position_broadcast_secs` | `setConfig(Config(position = cfg.copy(...)))` |
| Smart broadcast | `Switch` | `position_broadcast_smart_enabled` | `setConfig(...)` |
| Min smart distance (м) | `OutlinedTextField` | `broadcast_smart_minimum_distance` | `setConfig(...)` |

#### Секция D: Position Payload Flags

| Параметр | UI | Bitmask value |
|---|---|---|
| Include altitude | `Checkbox` | `PositionFlags.ALTITUDE` (1) |
| Include MSL altitude | `Checkbox` | `PositionFlags.ALTITUDE_MSL` (2) |
| Include heading | `Checkbox` ⭐ | `PositionFlags.HEADING` (256) |
| Include speed | `Checkbox` ⭐ | `PositionFlags.SPEED` (512) |
| Include timestamp | `Checkbox` | `PositionFlags.TIMESTAMP` (128) |
| Include sats in view | `Checkbox` | `PositionFlags.SATINVIEW` (32) |

> ⭐ HEADING + SPEED критичны для `directional_nodes_marks`

#### Секция E: Channel Position Precision

| Параметр | UI | Proto field | Запись |
|---|---|---|---|
| Position precision (primary channel) | `Dropdown` (0=Off / 10=~11km / 13=~1.4km / 16=~170m / 19=~21m / 32=Full) | `Channel.settings.module_settings.position_precision` | `radioController.setChannel(channel.copy(...))` |

> `position_precision = 0` — позиция не передаётся по каналу, независимо от всех остальных настроек. **Финальный стопор в цепочке.**

---

### Новые data-модели

**Файл**: `state/models/LocationConfigUi.kt`

```kotlin
data class LocationConfigUi(
    // Phone → Node
    val provideLocationToMesh: Boolean,
    val hasLocationPermission: Boolean,
    // Node Position Source
    val gpsMode: GpsModeUi,           // DISABLED / ENABLED / NOT_PRESENT
    val fixedPositionEnabled: Boolean,
    val fixedPositionLat: Double?,
    val fixedPositionLon: Double?,
    // Node → Mesh Broadcast
    val broadcastIntervalSecs: Int,    // UI default: 30
    val smartBroadcastEnabled: Boolean, // UI default: true
    val smartBroadcastMinDistanceM: Int, // UI default: 25
    // Payload Flags
    val positionFlags: Int,           // bitmask; UI default: HEADING|SPEED|ALTITUDE|TIMESTAMP = 897
    // Channel Position Precision
    val primaryChannelPositionPrecision: Int, // 0 = off, 32 = full; финальный стопор
)

enum class GpsModeUi { DISABLED, ENABLED, NOT_PRESENT }
```

**Расширить** `ConfigTabState`:

```kotlin
data class ConfigTabState(
    // ... existing fields ...
    val locationConfig: LocationConfigUi? = null,
)
```

---

### Новые use cases

| Use Case | Параметры | Действие |
|---|---|---|
| `ObserveLocationConfigUseCase` | `nodeNum: Int` | Combine(UiPrefs.shouldProvide, localConfigFlow.position, channelFlow[0]) → `LocationConfigUi` |
| `SetProvideLocationUseCase` | `nodeNum: Int, value: Boolean` | `uiPrefs.setShouldProvideNodeLocation(nodeNum, value)` |
| `WritePositionConfigUseCase` | `destNum: Int, config: PositionConfig` | `radioController.setConfig(destNum, Config(position = config), packetId)` |
| `WriteChannelPositionPrecisionUseCase` | `destNum: Int, channelIndex: Int, precision: Int` | `radioController.setChannel(destNum, channel.copy(position_precision = precision), packetId)` |
| `RemoveFixedPositionUseCase` | `destNum: Int` | `radioController.setFixedPosition(destNum, Position(0,0,0))` |

---

### Расширение MeshTestViewModel

Добавить в конструктор:
- `ObserveLocationConfigUseCase`
- `SetProvideLocationUseCase`
- `WritePositionConfigUseCase`
- `RemoveFixedPositionUseCase`

Добавить обработчики:
```kotlin
fun onProvideLocationToggle(enabled: Boolean)
fun onGpsModeChange(mode: GpsModeUi)
fun onRemoveFixedPosition()
fun onBroadcastIntervalChange(secs: Int)
fun onSmartBroadcastToggle(enabled: Boolean)
fun onPositionFlagsChange(flags: Int)
```

---

### Расширение ConfigTab и MeshTestScreen

`ConfigTab` получает дополнительные параметры:
```kotlin
locationConfig: LocationConfigUi?,
onProvideLocationToggle: (Boolean) -> Unit,
onGpsModeChange: (GpsModeUi) -> Unit,
onRemoveFixedPosition: () -> Unit,
onBroadcastIntervalChange: (Int) -> Unit,
onSmartBroadcastToggle: (Boolean) -> Unit,
onPositionFlagsChange: (Int) -> Unit,
```

---

## Рекомендуемые значения для live GPS с телефона

```
provide_location_to_mesh             = true           ← главный выключатель
gps_mode                             = DISABLED       ← не конкурировать с GPS чипом
fixed_position                       = false          ← не блокировать phone GPS
position_broadcast_secs              = 30             ← вместо 900 (минимальный рабочий)
position_broadcast_smart_enabled     = true           ← адаптивный бродкаст
broadcast_smart_minimum_distance     = 25             ← 25 метров
position_flags                       = HEADING(256) | SPEED(512) | ALTITUDE(1) | TIMESTAMP(128) = 897
primary_channel.position_precision   = 32             ← полная точность (проверить, не 0!)
```

---

## Sequence: полный флоу при включении

```
User: ConfigTab → "Provide location to mesh" → ON
  → SetProvideLocationUseCase(nodeNum, true) → DataStore
  → RemoveFixedPositionUseCase(nodeNum)       → Admin remove_fixed_position
  → MeshConnectionManagerImpl sees shouldProvide=true
      → commandSender.sendPosition(pos)       → POSITION_APP каждые 30 сек
          → LOC_EXTERNAL в пакете
          → Прошивка обновляет позицию ноды
          → Нода бродкастит по position_broadcast_secs (если 30 сек — каждые 30 сек)

Other nodes:
  → Принимают POSITION_APP
  → NodeManager.handleReceivedPosition()
  → GeoNodesTab обновляется
  → Карта обновляется
```

---

## Порядок реализации

```
1. Phase 1: MeshConnectionManagerImpl — bug fix (независим от UI)
2. Phase 2a: data-модели (LocationConfigUi, GpsModeUi) — новые файлы
3. Phase 2b: use cases — ObserveLocationConfigUseCase, SetProvideLocation, WritePositionConfig, RemoveFixedPosition
4. Phase 2c: MeshTestViewModel — добавить use cases + handlers
5. Phase 2d: ConfigTabState — добавить locationConfig поле
6. Phase 2e: LocationConfigCard Composable — новый файл
7. Phase 2f: ConfigTab — добавить LocationConfigCard, параметры
8. Phase 2g: MeshTestScreen — прокинуть новые handlers
```

---

## Риски

| Риск | Вероятность | Митигация |
|---|---|---|
| GPS-нода (T-Beam) перезаписывает LOC_EXTERNAL своим GPS | Средняя | gps_mode selector в UI — пользователь явно ставит DISABLED |
| `remove_fixed_position` отклонён по passkey | Низкая | При следующей попытке passkey обновится |
| `WritePositionConfigUseCase` отсутствует в domain — нужен новый UseCase | — | Добавить в Phase 2b |

---

## Change Log

- 2026-04-11: создан
- 2026-04-11: обновлён — добавлен реальный источник проблемы (тоггл выключен)
- 2026-04-11: переработан — ConfigTab вместо NodeSettings, полный анализ всех факторов флоу
- 2026-04-11: добавлен фактор #12 — channel position_precision (финальный стопор); секция E в ConfigTab; WriteChannelPositionPrecisionUseCase; исправлены дефолты UI (broadcast_secs=30, smart=true, dist=25, flags=897, precision=32)
