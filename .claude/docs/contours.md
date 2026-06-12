# Contours

**Date**: 2026-06-01
**Status**: Revised design (Primary mechanic + Exclusive mode + isolation guarantees)

Единый источник знаний по фиче Контур. Заменяет:
- `.claude/docs/contour-design.md` (archived)
- `.claude/docs/logical-channels-management.md` (archived)

Смежный документ: `.claude/docs/emergency-sos.md` (broadcast, UI, сообщения)

---

## Что такое Контур

Контур — именованный логический канал с PSK-ключом. Контур определяет:
- **транспорт**: имя канала + PSK для записи на ноду Meshtastic
- **маршрутизацию входящих пакетов**: `slot → hash → Contour`
- **участие в обмене**: `isActive` контролирует приём/передачу сообщений, геолокации, телеметрии
- **изоляцию групп**: неактивный контур не отображает чужие метки/ноды и не раскрывает свою геопозицию

Контур без транспорта невозможен: `transport: ContourTransport` non-nullable.

---

## Типы контуров

### Emergency

- Hardcoded объект, **не хранится в DB**
- **Не имеет `isActive` флага** — поведение определяется исключительно SOS-режимом
- **Primary только при активном SOS-режиме** (не по умолчанию)
- При не-SOS состоянии: занимает slot 1 (фиксировано)
- Нельзя удалить, нельзя редактировать
- В UI не отображается переключатель активности

#### Поведение Emergency в зависимости от SOS-режима

| Аспект | SOS выкл | SOS вкл |
|---|---|---|
| Приём пакетов | Да (всегда) | Да |
| Чужие ноды на карте | Нет¹ | Да |
| Входящие сообщения: уведомления | Нет (тихо) | Да |
| Входящие сообщения: счётчик непрочитанных | Нет | Да |
| Отправка геопозиции | Нет | Да (30s broadcast) |
| Отправка сообщений | Нет | Да |
| Signal tag на исходящих | — | Да (future) |

¹ **Future**: при наличии signal-tag в сообщении — показать ноду на карте даже без SOS-режима у получателя (обработка реального дистресса от чужих участников).

**Rationale**: Emergency-канал (LongFast, `AQ==`) — публичный канал, им пользуются все Meshtastic-устройства по умолчанию. Без фильтрации по SOS-режиму LongFast-трафик засоряет карту и чат. При этом пакеты всегда принимаются — для будущей обработки signal-tag.

### Default (Basic)

- `DefaultActiveContour` — seeded в DB при первом запуске
- `isActive = true` по умолчанию, назначается **Primary** по умолчанию
- Deletable, ничем не отличается от пользовательских контуров
- После удаления не восстанавливается

### Пользовательские

- Создаются пользователем (деблокируется после реализации QR/import sharing)
- Хранятся в DB
- Полный CRUD, управляемый `isActive`, могут становиться Primary

---

## Концепции

### Primary Contour

Ровно один контур является **Primary** в любой момент времени:

- Занимает **slot 0** на ноде
- **Нельзя деактивировать** пока Primary
- Выбирается пользователем через radio-button в списке контуров
- Смена Primary при подключённой ноде → **немедленный** `writeChannel(0, ...)` + `writeChannel` старого Primary на свободный слот (или удаление)
- Персистируется в DataStore: `primary_contour_id: String` (default = `DefaultActiveContour.ID`)

**Назначение Emergency Primary:** только через SOS-режим. Пользователь не может выбрать Emergency как Primary вручную.

### isActive

Контролирует участие контура в обмене. **Применяется ко всем контурам кроме Emergency** (у Emergency — SOS-режим, см. выше).

| isActive | Входящие сообщения | Исходящие сообщения | Гео (recv) | Гео (send) | Ноды на карте | Телеметрия |
|---|---|---|---|---|---|---|
| `true`  | доставляются в UI | разрешены | метки сохраняются + отображаются | отправляется | отображаются | обрабатывается |
| `false` | дропаются | блокируются | метки дропаются | блокируется | скрыты | дроп |

**Ноды на карте** (`ObserveNodeMarkersUseCase`) фильтруются по `isActive` контура, через который пришла позиция. Для этого `MeshNodeModel` должен нести `receivedOnSlot: Int?` (см. секцию Архитектура).

Правила:
- **Emergency**: управляется SOS-режимом, не `isActive`
- **Primary контур**: принудительно `true` пока Primary
- **Остальные**: user-configurable через dropdown-меню

Хранение: DB-колонка `is_active` для всех контуров кроме Emergency (у Emergency не хранится).

### Exclusive Contour

Контур с заданным `exclusivityTime: Instant?` может быть активирован в **эксклюзивном режиме**:

При активации:
1. Контур назначается Primary (slot 0)
2. **Все** остальные контуры → `isActive = false`
3. **Все** вторичные слоты ноды обнуляются (включая каналы, добавленные вне приложения)
4. На ноде остаётся: slot 0 = эксклюзивный контур, slot 1 = Emergency

Semantics `exclusivityTime`: если `exclusivityTime` задан и не истёк — контур доступен для эксклюзивной активации. После истечения — контур работает как обычный (exclusive-флаг игнорируется).

Применение: спортивные соревнования, где команды должны быть гарантированно изолированы.

**Статус**: модель и DB-колонка готовы, `ActivateExclusiveContourUseCase` — **planned**.
UI для выбора эксклюзивного режима — **deferred** (следующий таск).

---

## SOS-режим и Emergency как Primary

SOS-режим — отдельный концепт. Emergency не имеет `isActive` — только SOS-флаг управляет его поведением.

### При активации SOS (`TriggerEmergencyUseCase`)

1. Сохранить `pre_sos_primary_id` в DataStore
2. `setPrimaryContour(Emergency.id)` → `writeChannel(0, "LongFast", "AQ==")`
3. Запустить broadcast геопозиции (интервал 30s)
4. Отправить дистресс-сообщение с signal-tag (future)

### При отмене SOS (`CancelEmergencyUseCase`)

1. Остановить broadcast
2. Отправить all-clear сообщение с signal-tag (future)
3. Восстановить `pre_sos_primary_id` → `setPrimaryContour(preSosId)`
4. Очистить `pre_sos_primary_id`

### DataStore-ключи

```
primary_contour_id      String    default = DefaultActiveContour.ID
pre_sos_primary_id      String?   null когда SOS не активен
sos_mode_active         Boolean   default = false
```

### Geo send policy

Геопозиция отправляется **только на Primary контур** (slot 0). Канал определяется автоматически — тем, какой контур является Primary в данный момент:
- Нормальный режим → Basic (или любой пользовательский) в slot 0 → гео на Basic
- SOS-режим → Emergency становится Primary (slot 0) → гео на LongFast

`GeoSendPolicyImpl` не блокирует отправку по SOS-флагу. Отправка на неактивный контур невозможна структурно: гео всегда уходит на slot 0, а slot 0 = Primary = всегда `isActive`.

```kotlin
// GeoSendPolicyImpl — гео разрешено всегда; канал = Primary (slot 0)
observeAllowed() = flowOf(true)
// Канал выбирается mesh-слоем автоматически по slot 0
```

> **Удалить**: старый `observeAllowed() = observeSosMode().map { !it }` — логика инвертирована и некорректна.

---

## Архитектура

### Domain models

| Файл | Назначение |
|---|---|
| `domain/channel/model/ContourHash.kt` | `@JvmInline value class`, 8-hex-char SHA-256; `compute(name, psk)` |
| `domain/channel/model/ContourId.kt` | `@JvmInline value class` UUID |
| `domain/channel/model/Contour.kt` | `id, name, description?, expiration?, exclusivityTime?, isActive, transport` + ext `isEmergency` |
| `domain/channel/model/ContourTransport.kt` | `meshtastic: MeshtasticChannel` (future: satellite) |
| `domain/channel/model/MeshtasticChannel.kt` | `psk: String (base64), channelHash: ContourHash` |
| `domain/channel/model/DefaultContour.kt` | Hardcoded Emergency singleton |
| `domain/channel/model/DefaultActiveContour.kt` | Seed constants для DB-строки Basic |
| `domain/channel/model/ChannelSlotMaps.kt` | `slotToHash`, `hashToSlot` |
| `domain/channel/model/NodeChannelSlot.kt` | Один слот на физической ноде |
| `domain/channel/model/ChannelSyncStatus.kt` | `NotConnected / OnNode(slot) / NotOnNode / NoFreeSlot` |
| `domain/channel/ChannelSlotResolver.kt` | Interface: live-маппинг slot↔hash |

#### MeshNodeModel.receivedOnSlot — реализовано

```kotlin
data class MeshNodeModel(
    // ...
    val receivedOnSlot: Int? = null,  // slot из живого position-пакета (MeshPacket.channel)
)
```

Источник: `Node.positionChannel` → `NodeMapper`. Персистируется в Room DB (колонка `position_channel`).
Подробности реализации, жизненный цикл, обнаружение слота при подключении: `.claude/docs/position-channel-slot-discovery.md`.

### Repository interface

```kotlin
interface ContourRepository {
    fun observeContours(): Flow<List<Contour>>         // Emergency всегда prepend
    fun observePrimaryContourId(): Flow<ContourId>     // NEW
    fun observeSosMode(): Flow<Boolean>                // NEW (заменяет observeEmergencyIsActive)
    suspend fun setPrimaryContour(id: ContourId)       // NEW
    suspend fun setSosMode(active: Boolean)            // NEW
    suspend fun getPreSosPrimaryId(): ContourId?       // NEW
    suspend fun savePreSosPrimaryId(id: ContourId?)    // NEW
    suspend fun seedDefaultsIfAbsent()
    suspend fun saveContour(contour: Contour)
    suspend fun deleteContour(id: ContourId)
    suspend fun findByChannelHash(hash: ContourHash): Contour?
}
```

### Use cases

| Use Case | Статус | Изменения |
|---|---|---|
| `ObserveContoursUseCase` | Done | без изменений |
| `ObserveNodeChannelsUseCase` | Done | без изменений |
| `SaveContourUseCase` | Done | без изменений |
| `DeleteContourUseCase` | Done | без изменений |
| `ResolveChannelSlotUseCase` | Done | без изменений |
| `SetContourActiveUseCase` | Done → **Revised** | убрать спецслучай Emergency; guard: нельзя деактивировать Primary |
| `SyncContoursOnConnectUseCase` | Done → **Revised** | slot 0 = primaryContourId; slot 1 = Emergency; см. ниже |
| `SetPrimaryContourUseCase` | **Planned** | new: сохранить id + writeChannel(0) всегда (безопасен без соединения — guard в impl) |
| `ActivateExclusiveContourUseCase` | **Planned** | new: Primary + all inactive + clear foreign slots (writeChannel safe without connection) |
| `NodeProvisioningUseCase` | Done → **Revised** | pre-seed usedSlots={0,1}; skip primary contour; slots 2–7 only |
| `TriggerEmergencyUseCase` | Done → **Revised** | save preSosPrimary → setPrimary(Emergency) → broadcast |
| `CancelEmergencyUseCase` | Done → **Revised** | restore preSosPrimary → stop broadcast |
| `ObserveNodeMarkersUseCase` | Done → **Revised** | фильтр по `receivedOnSlot` + активности контура; Emergency-ноды — по SOS-режиму |
| `ObserveGeoNodesUseCase` | Done → **Revised** | то же: фильтр по `receivedOnSlot` + активности контура |

### Data layer

- `ContourRepositoryImpl`:
  - `observeContours()` = DB flow, Emergency всегда prepend (без `isActive` — SOS-режим)
  - DataStore-ключи: `primary_contour_id`, `pre_sos_primary_id`, `sos_mode_active`
  - Старый ключ `emergency_is_active` → мигрирует в `sos_mode_active`
- `GeoSendPolicyImpl`:
  - `observeAllowed() = flowOf(true)` — гео всегда разрешено; канал = slot 0 (Primary)
  - Удалить: `observeAllowed() = observeSosMode().map { !it }` (инвертированная логика — баг)
- `NodeMapper`:
  - `receivedOnSlot = positionChannel` — реализовано; подробности в `.claude/docs/position-channel-slot-discovery.md`
- `ChannelSlotResolverImpl`: без изменений

---

## Sync on Connect (пересмотренная схема)

### Нормальный режим

```
slot 0 = Primary контур
slot 1 = Emergency ("LongFast", "AQ==")
slots 2–7 = isActive non-emergency контуры (по одному на слот)
           если слот занят правильным контуром → skip
           если нет свободных слотов → Log.w
```

### Эксклюзивный режим (при activateExclusive)

```
slot 0 = эксклюзивный контур
slot 1 = Emergency
slots 2–7 = writeChannel(i, "", "") // обнуление всех
```

### Смена Primary (немедленно, при подключении)

```
writeChannel(0, newPrimary.name, newPrimary.pskBase64)
// старый Primary: перемещается на первый свободный слот или удаляется
```

---

## Routing (входящие пакеты)

**Единая точка резолва:** `ResolveContourFromSlotUseCase` — единственное место с правилами `slot → ContourResolution`.

Подробный operational guide: `.claude/docs/packet-channel-attribution.md`

```
incoming packet.channel (Int)
  → ResolveContourFromSlotUseCase(slot, contours, maps, primaryId, sosMode)
       → ContourResolution.Deliver | SilentStore | Drop
  → ApplyDeliveryPolicyUseCase(resolution, packetKind)
       → DeliveryPolicy.DELIVER | SILENT_STORE | DROP
```

Правила слотов (инварианты в `ResolveContourFromSlotUseCase`):
- slot 0 → `primaryContourId` (не через `slotToHash`)
- slot 1 → Emergency; SOS on → `Deliver`, SOS off → `SilentStore`
- slots 2–7 → `ChannelSlotResolver.slotToHash` → hash lookup → `Deliver` если `isActive`
- slot 8 (PKC) → `Drop` (out of scope MVP)

**Ноды на карте** — фильтруются через тот же резолвер в observe use case'ах:
```
node.receivedOnSlot
  == null      → скрыть
  == 0..N    → ResolveContourFromSlotUseCase → allowsDisplay()
               Deliver → показать; SilentStore/Drop → скрыть
```

Используется в: `IngestReceivedChatMessagesUseCase`, `IngestReceivedGeoMarksUseCase`, `ObserveNodeMarkersUseCase`, `ObserveGeoNodesUseCase`, `ObserveContourNodesUseCase`.

---

## Изоляция групп

Контуры обеспечивают изоляцию групп пользователей: команды на разных контурах не видят друг друга и не компрометируют свою геопозицию.

**Типичный кейс**: спортивные соревнования — две команды на разных контурах (уникальный PSK), эксклюзивный режим.

### Гарантии изоляции

| Вектор утечки | Механизм защиты |
|---|---|
| Входящие сообщения чужого контура | `isActive = false` → дроп в routing |
| Входящие гео-метки чужого контура | `isActive = false` → дроп в `IngestReceivedGeoMarksUseCase` |
| Ноды чужого контура на карте | `receivedOnSlot` + `isActive` фильтр в `ObserveNodeMarkersUseCase` |
| Отправка своей геопозиции в чужой контур | Гео уходит только на slot 0 (Primary); неактивные слоты не получают гео |
| Чужие ноды через Emergency (LongFast) | SOS = false → Emergency-ноды скрыты с карты |
| Расшифровка чужих пакетов | Уровень Meshtastic: PSK-шифрование; без ключа payload недоступен |

### Ограничения (known)

- Заголовок `MeshPacket` (nodeNum, shortName) нешифрован — противник видит факт существования наших нод в сети. Защита от этого — вне возможностей приложения.
- Emergency slot 1 присутствует на ноде всегда (публичный LongFast). Это осознанный компромисс: Emergency-трафик принимается для будущей обработки signal-tag. Позиция НЕ отправляется на Emergency вне SOS-режима.
- `receivedOnSlot == null` → нода скрыта (нет position-пакета с известным слотом).

---

## Presentation

- `ContourItem`: `id, name, description?, expiration?, exclusivityTime?, isActive, isEmergency, isPrimary, syncStatus`
- UI список: radio-button для Primary, dropdown-меню per contour
- Dropdown actions: `Set as Primary` / `Enable` / `Disable` / `Push to node` / `Delete`
- Emergency: dropdown — только SOS-кнопка; Set as Primary, Enable/Disable — скрыты
- Primary контур: `Disable` недоступен в dropdown

---

## TODO (deferred)

```kotlin
// TODO(contour): ActivateExclusiveContourUseCase — UI trigger (следующий таск)
// TODO(contour): обработать отсутствие свободных слотов (UI уведомление)
// TODO(contour): DROP COLUMN meshtastic_slot when minSdk ≥ 35

// DONE(contour/isolation): MeshNodeModel.receivedOnSlot + ResolveContourFromSlotUseCase в observe use cases

// TODO(contour/isolation): GeoSendPolicyImpl.observeAllowed() исправить на flowOf(true)
//   Текущая логика observeSosMode().map { !it } инвертирована и некорректна

// TODO(contour/emergency): signal-tag в дистресс-сообщениях Emergency
//   Формат: TBD (префикс или поле DataPacket)
//   Использование: при SOS inactive — Emergency-сообщение с tag → уведомить + показать ноду на карте

// NOTE(contour/testing): SOS-тест на реальных девайсах = спам на публичный LongFast
//   Протокол: перед тестом временно изменить DefaultContour.CHANNEL_NAME + OPEN_PSK
//   на изолированные тестовые значения (см. Phase 3.5 в плане contours-redesign.md).
//   После подтверждения тестировщика — обязательно восстановить дефолты (Phase 4.5).
//   Тестовые значения НЕ коммитятся.
```
