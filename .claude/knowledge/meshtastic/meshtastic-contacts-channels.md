# Meshtastic — контакты, каналы, адресация

> Источники: `mesh/model/DataPacket.kt`, `mesh/data/manager/MeshDataHandlerImpl.kt`,
> `mesh/repository/usecase/SendMessageUseCase.kt`
> Обновлять при смене mesh-слоя.

---

## Адресация нод

### Специальные адреса

| Константа | Значение | Смысл |
|---|---|---|
| `DataPacket.ID_BROADCAST` | `"^all"` | Широковещательный адрес (все ноды в канале) |
| `DataPacket.ID_LOCAL` | `"^local"` | Локальная нода (заполняется при отправке, если nodeNum неизвестен) |
| `DataPacket.NODENUM_BROADCAST` | `0xFFFFFFFF` | Числовой эквивалент `^all` в protobuf-пакете |

### Обычные ноды
Адрес ноды в строковом виде: `"!{8-char-hex}"`, например `"!ab1234cd"`.  
`DataPacket.nodeNumToDefaultId(num: Int): String = "!%08x".format(num)`

---

## Формат `contactKey`

`contactKey` — первичный ключ контакта в Room. Формируется в `MeshDataHandlerImpl.rememberDataPacket()`:

```kotlin
val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from
val contactKey = "${dataPacket.channel}$contactId"
```

**Примеры:**

| Тип | contactKey | Расшифровка |
|---|---|---|
| Канал 0, широковещание | `"0^all"` | channel=0, to=`^all` |
| Канал 1, широковещание | `"1^all"` | channel=1, to=`^all` |
| DM с нодой | `"0!ab1234cd"` | channel=0, nodeId=`!ab1234cd` |
| DM через канал 2 | `"2!ab1234cd"` | channel=2, nodeId |

**Правило**: при `fromLocal || toBroadcast` используется `to`, иначе `from`.  
Для исходящих сообщений (`fromLocal = true`) contactKey всегда по `to`.  
Для входящих DM contactKey — по отправителю (`from`).

---

## Каналы Meshtastic

### Слоты на ноде
Нода поддерживает 8 слотов каналов (индексы 0–7). Каждый слот — `Channel` в `ChannelSet`.

| Слот | Роль | Назначение |
|---|---|---|
| 0 | PRIMARY | Основной канал (broadcast, position, messaging) |
| 1–7 | SECONDARY | Дополнительные каналы, чаще для шифрованных групп |
| 8 | PKC_CHANNEL_INDEX | Зарезервировано для PKC DM (Public Key Cryptography) |

### Дефолтный канал
Слот 0, имя `"LongFast"`, PSK = `AQ==` (один байт `0x01`, означает дефолтный ключ прошивки).

### PSK
- Хранится как Base64-строка в protobuf `Channel.settings.psk`
- Длина: 0 байт (шифрование отключено), 16 байт (AES-128), 32 байта (AES-256)
- `AQ==` (1 байт) = специальный флаг прошивки «использовать встроенный ключ»

---

## Отправка текстового сообщения

**Точка входа** (mesh-слой): `SendMessageUseCase.invoke(text, contactKey)`

Дефолтный `contactKey = "0${DataPacket.ID_BROADCAST}"` = `"0^all"` (channel 0, broadcast).

Внутри создаётся `DataPacket`:
```kotlin
// DataPacket.kt — конструктор для текста
DataPacket(
    to      = contactKey.drop(1),  // всё после индекса канала, т.е. "^all" или "!ab1234cd"
    channel = contactKey[0].digitToInt(),  // первый символ = индекс канала
    text    = text,
    // → bytes = text.encodeToByteArray().toByteString()
    // → dataType = PortNum.TEXT_MESSAGE_APP.value = 1
)
```

Затем `CommandSenderImpl.sendData(p)` → `sendNow(p)` → строится `MeshPacket`:
```kotlin
MeshPacket(
    from      = myNodeNum,
    to        = resolveNodeNum(p.to),   // "^all" → NODENUM_BROADCAST (0xFFFFFFFF)
    channel   = p.channel,
    want_ack  = p.wantAck,              // default true
    hop_limit = computeHopLimit(),      // из localConfig.lora.hop_limit, дефолт 3
    decoded   = Data(
        portnum  = PortNum.TEXT_MESSAGE_APP,
        payload  = p.bytes,
    ),
)
```

Пакет уходит в `PacketHandler.sendToRadio()` → записывается в BLE-характеристику `TORADIO`.

---

## Доставка и статусы

```kotlin
enum class MessageStatus {
    UNKNOWN,        // не задан
    RECEIVED,       // получен из mesh
    QUEUED,         // ожидает соединения с радио
    ENROUTE,        // передан радио, ACK ещё не пришёл
    DELIVERED,      // получен ACK
    SFPP_ROUTING,   // в процессе маршрутизации через Store&Forward
    SFPP_CONFIRMED, // подтверждён через Store&Forward
    ERROR,          // пришёл NAK, не доставлен
}
```

**`want_ack = true` (default)**: отправитель ждёт ACK от ноды-получателя.  
Широковещание (`^all`) ACK не возвращает — сообщение остаётся в `ENROUTE`.

---

## Разрешение адреса (String → Int)

```kotlin
// CommandSenderImpl.resolveNodeNum()
fun resolveNodeNum(toId: String): Int = when (toId) {
    "^all"  -> NODENUM_BROADCAST          // 0xFFFFFFFF
    else    -> toId.substring(1).toLong(16).toInt()  // "!ab1234cd" → num
            ?: nodeManager.nodeDBbyID[toId]?.num      // fallback из DB
}
```

---

## Admin-канал

Admin-команды (setFixedPosition, setChannel, etc.) отправляются на специальный **admin-канал**:
- Если оба узла поддерживают PKC → `PKC_CHANNEL_INDEX = 8`
- Иначе → слот с именем `"admin"` в ChannelSet, или слот 0
- `MeshPacket.Priority.RELIABLE` + `want_ack = true`

---

## Связь с `ChatContact.id` (domain-слой)

В текущей реализации `ChatContact.id` = `contactKey` (например, `"0^all"`, `"0!ab1234cd"`).  
По спецификации (`.claude/specs/channels-and-identity.md`) планируется рефакторинг:  
`ContactType.CHANNEL` будет идентифицироваться `LogicalChannelId` (UUID), а не Meshtastic-спецификой.
