# Contours

**Date**: 2026-05-29
**Status**: Revised design (Primary mechanic + Exclusive mode)

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

Контур без транспорта невозможен: `transport: ContourTransport` non-nullable.

---

## Типы контуров

### Emergency

- Hardcoded объект, **не хранится в DB**
- `isActive = true` принудительно — инвариант, не настраивается пользователем
  - Rationale: Emergency-канал принимает запросы помощи от любых участников, в т.ч. без приложения
- **Primary только при активном SOS-режиме** (не по умолчанию)
- При не-SOS состоянии: занимает один из вторичных слотов (1–7)
- Нельзя удалить, нельзя редактировать, нельзя деактивировать
- `isActive` в UI не отображается как переключатель

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

Контролирует участие контура в обмене:

| isActive | Входящие | Исходящие | Гео (recv) | Гео (send) | Телеметрия |
|---|---|---|---|---|---|
| `true`  | доставляются в UI | разрешены | обрабатывается | отправляется | обрабатывается |
| `false` | дропаются | блокируются | дроп | блокируется | дроп |

Правила:
- **Emergency**: всегда `true`, не изменяется
- **Primary контур**: принудительно `true` пока Primary
- **Остальные**: user-configurable через dropdown-меню

Хранение: DB-колонка `is_active` для всех контуров кроме Emergency (у Emergency не хранится — hardcoded).

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

SOS-режим — отдельный концепт, не тождественен `isActive` Emergency.

### При активации SOS (`TriggerEmergencyUseCase`)

1. Сохранить `pre_sos_primary_id` в DataStore
2. `setPrimaryContour(Emergency.id)` → `writeChannel(0, "LongFast", "AQ==")`
3. Запустить broadcast геопозиции (интервал 30s)
4. Отправить дистресс-сообщение

### При отмене SOS (`CancelEmergencyUseCase`)

1. Остановить broadcast
2. Отправить all-clear сообщение
3. Восстановить `pre_sos_primary_id` → `setPrimaryContour(preSosId)`
4. Очистить `pre_sos_primary_id`

### DataStore-ключи

```
primary_contour_id      String    default = DefaultActiveContour.ID
pre_sos_primary_id      String?   null когда SOS не активен
sos_mode_active         Boolean   default = false
```

### Geo protection

```kotlin
// GeoSendPolicyImpl
observeAllowed() = observeSosMode().map { !it }
// НЕ observeEmergencyIsActive() — SOS-режим отвязан от isActive Emergency
```

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

### Data layer

- `ContourRepositoryImpl`:
  - `observeContours()` = DB flow, Emergency всегда prepend (isActive = true hardcoded)
  - DataStore-ключи: `primary_contour_id`, `pre_sos_primary_id`, `sos_mode_active`
  - Старый ключ `emergency_is_active` → мигрирует в `sos_mode_active`
- `GeoSendPolicyImpl`:
  - `observeAllowed() = contourRepo.observeSosMode().map { !it }`
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

```
incoming packet.channel (Int)
  slot == 0  → primaryContour (читаем из observePrimaryContourId)
  slot == 1  → DefaultContour.asContour() (Emergency, isActive = true)
  slot N     → ChannelSlotResolver.slotToHash[N] → ContourHash (или drop + Log.w)
             → contours.find { hash match } (или drop + Log.w)
  takeIf { it.isActive } → deliver (или drop если inactive)
```

Используется в: `IngestReceivedGeoMarksUseCase`, `MeshToChatAdapter`.

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
```
