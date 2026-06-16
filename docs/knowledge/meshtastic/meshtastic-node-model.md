# Meshtastic — модель ноды (Node)

> Источник истины: `mesh/src/main/kotlin/.../mesh/model/Node.kt` и `NodeEntity.kt`
> Обновлять при смене mesh-слоя.

---

## Основная структура `Node`

```kotlin
data class Node(
    val num: Int,               // nodeNum (uint32 as Int) — уникальный идентификатор ноды в сети
    val user: User,             // protobuf User: id, long_name, short_name, hw_model, role
    val position: Position,     // protobuf Position: latitude_i, longitude_i, altitude, time
    val lastHeard: Int,         // Unix seconds — последний раз видели ноду в эфире
    val snr: Float,             // SNR последнего пакета (Float.MAX_VALUE = нет данных)
    val rssi: Int,              // RSSI последнего пакета (Int.MAX_VALUE = нет данных)
    val deviceMetrics: DeviceMetrics, // заряд батареи, voltage, channel utilization
    val channel: Int,           // индекс канала (0–7), на котором нода обычно отвечает
    val hopsAway: Int,          // -1 = нет данных; 0 = прямое соединение; N = через N узлов
    val isFavorite: Boolean,
    val isIgnored: Boolean,
    val isMuted: Boolean,
    val environmentMetrics: EnvironmentMetrics,
    val viaMqtt: Boolean,       // пришёл ли последний пакет через MQTT
)
```

---

## Идентификаторы ноды

| Поле | Тип | Пример | Откуда |
|---|---|---|---|
| `node.num` | `Int` | `2864434397` | число из эфира (uint32 как Int) |
| `node.user.id` | `String` | `"!ab1234cd"` | строковое представление nodeNum |

**Конвертация:**
```kotlin
// num → id
fun nodeNumToDefaultId(n: Int): String = "!%08x".format(n)  // DataPacket.nodeNumToDefaultId()

// id → num
fun idToDefaultNodeNum(id: String?): Int? = id?.substring(1)?.toLongOrNull(16)?.toInt()
```

Прошивка добавляет `!` перед hex-представлением. Если пользователь задаёт имя ноды вручную, `user.id` всё равно остаётся стандартным `!{8hex}`.

---

## Поля `User`

```kotlin
// protobuf org.meshtastic.proto.User
user.id         // String: "!ab1234cd"
user.long_name  // String: до 39 символов (позывной, отображается на карте и в чате)
user.short_name // String: 1–4 символа (отображается на иконке ноды)
user.hw_model   // HardwareModel enum (T-Beam, Heltec, TLORA, UNSET...)
user.role       // Config.DeviceConfig.Role (CLIENT, ROUTER, REPEATER, TRACKER...)
user.public_key // ByteString: ключ для PKC-шифрования (пустой = PKC недоступен)
```

---

## Позиция

```kotlin
// protobuf org.meshtastic.proto.Position
position.latitude_i   // Int?: градусы × 1e7 (например, 55.7558° → 557558000)
position.longitude_i  // Int?: градусы × 1e7
position.altitude     // Int?: метры над уровнем моря
position.time         // Int: Unix seconds — МОЖЕТ БЫТЬ 0 (прошивка не всегда ставит)
position.ground_speed // Int?: скорость в м/с (0 = стоит или нет данных)
position.ground_track // Int?: курс в градусах × 1e-5 (0 = нет данных)
position.sats_in_view // Int: количество видимых спутников
```

**Конвертация в double:**
```kotlin
val latitude  = (position.latitude_i  ?: 0) * 1e-7
val longitude = (position.longitude_i ?: 0) * 1e-7
```

**Валидность позиции** (из `Node.hasValidPosition()`):
```kotlin
fun hasValidPosition(): Boolean =
    latitude  != 0.0 && longitude != 0.0 &&
    latitude  in -90.0..90.0 &&
    longitude in -180.0..180.0
```

**Критичная особенность `position.time == 0`:**  
Многие прошивки (особенно для нод без собственного GPS) не заполняют поле `time` в Position пакете.  
Когда нужна метка времени позиции — брать `position.time.takeIf { it > 0 } ?: node.lastHeard`.

---

## Online / Offline

```kotlin
// mesh/src/.../model/util/TimeUtils.kt
private val ONLINE_WINDOW_HOURS = 2.hours
fun onlineTimeThreshold(): Int = (nowInstant - ONLINE_WINDOW_HOURS).epochSeconds.toInt()

// Node.isOnline:
val isOnline: Boolean get() = lastHeard > onlineTimeThreshold()
```

**Нода считается online, если `lastHeard` > `(сейчас - 2 часа)` в Unix seconds.**

`lastHeard` обновляется при каждом входящем пакете от ноды — не только Position, но и Telemetry, TextMessage, и т.д.

---

## DeviceMetrics (телеметрия)

```kotlin
// node.deviceMetrics
deviceMetrics.battery_level     // Int?: 0–100 (%), null или 0 = нет данных; 101 = зарядка
deviceMetrics.voltage            // Float?: напряжение батареи (вольты)
deviceMetrics.channel_utilization // Float?: загрузка канала в % (0..100)
deviceMetrics.air_util_tx        // Float?: TX-загрузка канала в %
deviceMetrics.uptime_seconds     // Int?: аптайм прошивки в секундах

// Удобный геттер в Node:
val batteryLevel get() = deviceMetrics.battery_level  // Int?
val voltage      get() = deviceMetrics.voltage         // Float?
val batteryStr   get() = if ((batteryLevel ?: 0) in 1..100) "$batteryLevel%" else ""
```

**Телеметрия рассылается нодой периодически.** Дефолтный интервал прошивки — 3600 с (1 час). Может быть настроен пользователем.

---

## EnvironmentMetrics

```kotlin
// node.environmentMetrics (только для нод с сенсорами)
environmentMetrics.temperature       // Float?
environmentMetrics.relative_humidity // Float?
environmentMetrics.barometric_pressure // Float?
environmentMetrics.voltage           // Float? (независимо от battery voltage)
environmentMetrics.iaq               // Int? (Indoor Air Quality)
```

Большинство игровых/тактических нод `environmentMetrics` не имеют — поле будет дефолтным (`EnvironmentMetrics()`).

---

## Роли нод, которые нельзя мессаджить

```kotlin
// Node.kt
fun Config.DeviceConfig.Role?.isUnmessageableRole(): Boolean = this in listOf(
    Role.REPEATER, Role.ROUTER, Role.ROUTER_LATE,
    Role.SENSOR, Role.TRACKER, Role.TAK_TRACKER,
)
```

Эти ноды не читают текстовые сообщения — только ретранслируют или отправляют данные.
