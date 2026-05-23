# Meshtastic — пакеты и PortNum

> Источники: `mesh/model/DataPacket.kt`, `mesh/data/manager/CommandSenderImpl.kt`
> Protobuf: `org.meshtastic.proto.*` (wire-generated)

---

## Иерархия пакетов

```
MeshPacket (protobuf, уходит в TORADIO characteristic)
├── from: Int          — nodeNum отправителя
├── to: Int            — nodeNum получателя (0xFFFFFFFF = broadcast)
├── id: Int            — уникальный ID пакета (генерируется локально)
├── want_ack: Boolean
├── hop_limit: Int     — дефолт 3 (из localConfig.lora.hop_limit)
├── hop_start: Int     — = hop_limit при отправке
├── channel: Int       — индекс слота (0–7; 0 = PRIMARY)
├── priority: Priority — UNSET / BACKGROUND / RELIABLE / etc.
├── pki_encrypted: Boolean
├── public_key: ByteString  — для PKC DM
└── decoded: Data
         ├── portnum: PortNum     — тип payload
         ├── payload: ByteString  — содержимое (зависит от portnum)
         ├── want_response: Boolean
         ├── reply_id: Int        — ID сообщения, на которое отвечаем
         ├── dest: Int            — для traceroute/telemetry запросов
         └── emoji: Int           — реакция на сообщение
```

---

## PortNum — типы пакетов

| Enum | Value | Payload | Используется в |
|---|---|---|---|
| `UNKNOWN_APP` | 0 | — | — |
| `TEXT_MESSAGE_APP` | 1 | `ByteString` UTF-8 текст | Чат |
| `REMOTE_HARDWARE_APP` | 2 | — | — |
| `POSITION_APP` | 3 | protobuf `Position` | GPS-позиция |
| `NODEINFO_APP` | 4 | protobuf `User` | Информация о ноде |
| `ROUTING_APP` | 5 | protobuf `Routing` | ACK / NAK / delivery |
| `ADMIN_APP` | 67 | protobuf `AdminMessage` | Настройка ноды |
| `WAYPOINT_APP` | 71 | protobuf `Waypoint` | Метки на карте |
| `TELEMETRY_APP` | 67 | protobuf `Telemetry` | Батарея, сенсоры |
| `TRACEROUTE_APP` | 70 | protobuf `RouteDiscovery` | Маршрут пакета |
| `ALERT_APP` | 72 | `ByteString` UTF-8 текст | Критическое сообщение |
| `NEIGHBORINFO_APP` | 69 | protobuf `NeighborInfo` | Соседние ноды |
| `PAXCOUNTER_APP` | 68 | protobuf `Paxcount` | Счётчик устройств |

> Точные значения enum — в сгенерированном файле `PortNum.kt` (wire protobuf).
> Для `TEXT_MESSAGE_APP` важно именно значение `1` — используется в Room `port_num` колонке.

---

## DataPacket (Kotlin-обёртка над MeshPacket)

`DataPacket` — промежуточная модель между mesh-слоем и доменом. Хранится в Room.

```kotlin
data class DataPacket(
    var to: String?,         // "^all" или "!ab1234cd" — строковый nodeId
    var bytes: ByteString?,  // payload
    var dataType: Int,       // PortNum.value
    var from: String?,       // строковый nodeId отправителя или "^local"
    var time: Long,          // millis since 1970 (время отправки/получения)
    var id: Int,             // 0 = не задан
    var status: MessageStatus?,
    var hopLimit: Int,
    var channel: Int,        // индекс слота 0–7
    var wantAck: Boolean,    // default true
    var snr: Float,
    var rssi: Int,
    var replyId: Int?,       // ID пакета, на который это ответ
    var emoji: Int,
    var viaMqtt: Boolean,
)
```

**Конструктор для текстового сообщения:**
```kotlin
DataPacket(to = "^all", channel = 0, text = "Hello")
// → bytes = "Hello".encodeToByteArray().toByteString()
// → dataType = PortNum.TEXT_MESSAGE_APP.value (= 1)
```

**Конструктор для Waypoint:**
```kotlin
DataPacket(to = "^all", channel = 0, waypoint = Waypoint(...))
// → bytes = Waypoint.ADAPTER.encode(waypoint).toByteString()
// → dataType = PortNum.WAYPOINT_APP.value
```

---

## Position пакет (подробно)

```kotlin
// protobuf org.meshtastic.proto.Position
Position(
    latitude_i  = (55.7558 * 1e7).toInt(),   // = 557558000
    longitude_i = (37.6173 * 1e7).toInt(),   // = 376173000
    altitude    = 150,                         // метры над уровнем моря
    time        = (System.currentTimeMillis() / 1000).toInt(), // Unix seconds — МОЖЕТ БЫТЬ 0
    ground_speed = 5,          // м/с
    ground_track = 18000000,   // курс в градусах × 1e-5 (180° → 18000000)
    sats_in_view = 8,
    precision_bits = 32,       // точность (чем меньше, тем грубее позиция)
)
```

**Конвертация:**
```kotlin
val lat = (position.latitude_i  ?: 0) * 1e-7  // Double, degrees
val lon = (position.longitude_i ?: 0) * 1e-7  // Double, degrees
```

**`position.time == 0`** — частый случай, особенно для нод без GPS:
- Прошивка не всегда заполняет `time` в Position пакете
- Fallback: использовать `node.lastHeard` как оценку возраста позиции
- Паттерн: `val effectiveTime = position.time.takeIf { it > 0 } ?: node.lastHeard`

---

## Ограничения размера payload

```kotlin
// Constants.DATA_PAYLOAD_LEN — максимум для Data.payload
// Типичное значение: 237 байт (Meshtastic default MTU)
// Проверка в CommandSenderImpl.sendData():
if (!Data.ADAPTER.isWithinSizeLimit(data, Constants.DATA_PAYLOAD_LEN.value)) {
    error("Message too long: $actualSize bytes")
}
```

Текстовые сообщения: лимит ~230 UTF-8 байт с учётом overhead заголовков.

---

## Waypoint (метка)

```kotlin
// protobuf org.meshtastic.proto.Waypoint
Waypoint(
    id          = uniqueId,      // Int: уникальный ID метки
    name        = "Checkpoint",  // до 30 символов
    description = "Место сбора", // до 100 символов
    expire      = 0,             // Unix seconds, 0 = не истекает
    locked_to   = 0,             // nodeNum владельца, 0 = доступна всем
    latitude_i  = (lat * 1e7).toInt(),
    longitude_i = (lon * 1e7).toInt(),
    icon        = 0,             // emoji codepoint или 0
)
```

Waypoint отправляется через `DataPacket(to, channel, waypoint)` с `PortNum.WAYPOINT_APP`.

**Rate limit (прошивка):** пакеты `WAYPOINT_APP` (и `ALERT_APP`) с телефона через PhoneAPI не чаще **1 раз в 10 секунд** — более частая отправка отбрасывается на узле. В MeshTactics `GeoMarkRepositoryImpl.MIN_SEND_INTERVAL_MS = 10_500`.

---

## Admin-команды (примеры)

Отправляются через `CommandSenderImpl.sendAdmin()` в виде protobuf `AdminMessage`:

```kotlin
// Установить фиксированную позицию
AdminMessage(set_fixed_position = meshPosition)

// Снять фиксированную позицию
AdminMessage(remove_fixed_position = true)

// Установить канал
AdminMessage(set_channel = channel)

// Сброс настроек
AdminMessage(factory_reset = true)
```

Admin-пакеты: `Priority.RELIABLE`, `want_ack = true`, идут на admin-канал (см. `meshtastic-contacts-channels.md`).
