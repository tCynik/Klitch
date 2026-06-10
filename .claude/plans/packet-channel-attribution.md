# Plan: Packet Channel Attribution — явная привязка канала к входящим сущностям

**Date**: 2026-06-05
**Updated**: 2026-06-10
**Status**: Done — Phase 1–4 + Phase 6 (docs). Phase 5 optional, не реализована

## Summary

Централизовать и формализовать определение канала (контура) для всех входящих сущностей:
сообщений, геометок, геопозиций нод, телеметрии и событий. Сейчас правила маршрутизации
`slot → Contour` дублируются в 4+ use case'ах, а идентификация канала смешивает транспортный
слот Meshtastic (`MeshPacket.channel`) с логическим контуром приложения (`ContourId`) через
хрупкий строковый `contactKey`.

Цель: **одна точка резолва**, **два явных уровня идентичности**, **каждая сохраняемая сущность
несёт `contourId`** (или явный transport slot там, где состояние перезаписывается).

Связанные документы:
- `.claude/docs/contours.md` — routing rules, isolation guarantees
- `.claude/specs/channels-and-identity.md` — LogicalChannel, AppUser, storage principle
- `.claude/knowledge/meshtastic/meshtastic-contacts-channels.md` — contactKey, слоты
- `.claude/knowledge/meshtastic/meshtastic-packets.md` — MeshPacket.channel, PortNum
- `.claude/archive/logical-channel-identity-refactor.md` — channelHash, ChannelSlotResolver
- `.claude/plans/contours-redesign.md` — receivedOnSlot, Emergency silent mode (Done)

---

## Проблема

### Что нужно знать однозначно

Для каждого входящего пакета приложение должно ответить на два вопроса:

1. **Из какого транспортного слота** пришёл пакет? (`Int` 0–7, редко 8 для PKC DM)
2. **К какому контуру** (логическому каналу) он относится? (`ContourId`)

Без этого невозможна изоляция групп: неактивный контур продолжит показывать чужие ноды,
сообщения и метки.

### Текущее состояние (актуально на 2026-06-10)

| Сущность | Как определяется канал сейчас | Где хранится |
|---|---|---|
| Сообщения | inline `when(channelIndex)`, **slot 1 (Emergency) не обрабатывается** — попадает в `else` → hash lookup без SOS-gate | `chat_message.logical_channel_id` |
| Геометки | inline `when(packet.channel)`, slot 0/1/else — slot 1 SOS-gated ✓ | `geo_mark` через `contour.id` |
| Позиция ноды | **фильтрация отсутствует** (KDoc «intentionally absent» — ошибочное обоснование). Emergency-ноды видны вне SOS | `receivedOnSlot` для name lookup в `ObserveContourNodesUseCase` |
| Телеметрия | без привязки к контуру | — |
| NODEINFO | без привязки к контуру | — |

### Дублирование логики резолва

Актуальные дубли `when (slot)`:

- `IngestReceivedChatMessagesUseCase` — inline резолв, **без slot 1 Emergency gate**
- `IngestReceivedGeoMarksUseCase` — inline резолв, slot 1 корректен

**Observe use cases намеренно убрали channel-based фильтрацию** (см. KDoc в каждом файле):
> "Channel-based contour filtering is intentionally absent. MeshPacket.channel reflects the LOCAL channel index on the receiving radio — PSK membership already enforced at hardware level."

`ObserveContourNodesUseCase` использует `slotToHash` только для **labeling** (имя контура у ноды), не для фильтрации. Gap: slot 0 не в `slotToHash` → `contourName = null` для Primary-нод.

### Хрупкости

- `contactKey` как строка `"0^all"` / `"2!ab1234cd"` — парсится через `first().digitToInt()`
- `node.channel` ≠ `node.positionChannel` — разная семантика, легко перепутать
- Нет единого типа `PacketAttribution` — каждый ingest сам решает drop/deliver
- Emergency SOS-gate реализован в `IngestReceivedGeoMarksUseCase`, но **отсутствует** в `IngestReceivedChatMessagesUseCase`

---

## Архитектурное решение

### Два уровня идентичности

```
Transport layer (mesh)          Domain layer (app)
─────────────────────          ──────────────────
MeshPacket.channel: Int   →    ContourId (UUID)
TransportSlot (0–7, 8=PKC)     Contour (name, psk, isActive, …)
```

**Правило:** канал **никогда** не выводится из `from`/`to`, имени ноды или `contactKey`.
Единственный авторитетный транспортный сигнал — `MeshPacket.channel` (или его копия в `DataPacket.channel`).

### Цепочка обработки

```
[нода] MeshPacket прибыл
  → MeshDataMapper: DataPacket.channel = packet.channel (или PKC_CHANNEL_INDEX=8)
  → ResolveContourFromSlotUseCase(slot, contours, maps, primaryId, sosMode)
       → ContourResolution: Deliver | SilentStore | Drop
  → DeliveryPolicy по PortNum (что делать с результатом)
  → INSERT / UPDATE с contourId или transportSlot
  → domain читает только из своего хранилища
```

### Новые domain-модели

```kotlin
// domain/channel/model/TransportSlot.kt
@JvmInline value class TransportSlot(val value: Int)

// domain/channel/model/ContourResolution.kt
sealed interface ContourResolution {
    data class Deliver(val contour: Contour) : ContourResolution
    /** Emergency вне SOS: принять, но без UI-доставки */
    data class SilentStore(val contour: Contour) : ContourResolution
    data class Drop(val reason: String) : ContourResolution
}

// domain/channel/model/DeliveryPolicy.kt
enum class DeliveryPolicy {
    DELIVER,       // показать в UI, уведомить, сохранить
    SILENT_STORE,  // сохранить без уведомлений (Emergency chat вне SOS)
    DROP,          // не сохранять, не показывать
}

// domain/channel/model/PacketAttribution.kt
data class PacketAttribution(
    val slot: TransportSlot,
    val senderNodeId: String,
    val contourId: ContourId?,
    val resolution: ContourResolution,
)
```

### ResolveContourFromSlotUseCase (ядро)

Единственное место с правилами `slot → Contour`:

```kotlin
class ResolveContourFromSlotUseCase {
    operator fun invoke(
        slot: Int,
        contours: List<Contour>,
        maps: ChannelSlotMaps,
        primaryContourId: ContourId,
        sosMode: Boolean,
    ): ContourResolution = when (slot) {
        0 -> contours.find { it.id == primaryContourId }
            ?.let { ContourResolution.Deliver(it) }
            ?: ContourResolution.Drop("primary contour not found")

        1 -> contours.find { it.isEmergency }?.let { emergency ->
            if (sosMode) ContourResolution.Deliver(emergency)
            else ContourResolution.SilentStore(emergency)
        } ?: ContourResolution.Drop("emergency contour not found")

        else -> {
            val hash = maps.slotToHash[slot]
                ?: return ContourResolution.Drop("unknown slot $slot")
            val contour = contours.find { it.transport.meshtastic.channelHash == hash }
                ?: return ContourResolution.Drop("no contour for hash $hash")
            if (!contour.isActive) ContourResolution.Drop("contour inactive: ${contour.name}")
            else ContourResolution.Deliver(contour)
        }
    }
}
```

**Инварианты:**
- slot 0 **всегда** = `primaryContourId`, не через `slotToHash` (Primary может меняться пользователем)
- slot 1 **всегда** = Emergency по конвенции (фиксированный PSK)
- slots 2–7 = runtime lookup через `ChannelSlotResolver`
- slot 8 (PKC DM) — отдельная ветка (out of scope MVP, но зарезервировать в `when`)

### DeliveryPolicy по типу пакета

Резолв слота одинаков для всех типов. Различается **что делать** с результатом:

| PortNum | Deliver (active contour) | SilentStore (Emergency, SOS off) | Drop |
|---|---|---|---|
| TEXT_MESSAGE_APP | UI + unread + persist | persist, no notification | — |
| POSITION_APP | update positionChannel, show on map | — | не обновлять / не показывать |
| WAYPOINT_APP | persist geo mark | — | drop packet |
| TELEMETRY_APP | process + show in Network | — | drop |
| NODEINFO_APP | update user info | — | drop |
| ALERT_APP | TBD (signal-tag future) | TBD | — |

Функция-обёртка:

```kotlin
fun applyDeliveryPolicy(
    resolution: ContourResolution,
    portNum: PortNum,
    sosMode: Boolean,
): Pair<ContourResolution, DeliveryPolicy>
```

---

## Правила хранения по типу сущности

| Сущность | Сохранять при получении | Фильтрация при чтении |
|---|---|---|
| Сообщение | `contourId` + `packetId` | `WHERE logical_channel_id = ?` |
| Геометка | `contourId` + `waypointId` | TTL + contour scope |
| Позиция ноды | `positionChannel` (transport slot) | `ResolveContourFromSlot` при observe |
| Телеметрия | `telemetryChannel: Int?` (**new**) | contour filter в Network screen |
| NODEINFO | `nodeInfoChannel: Int?` (**new**, optional) | — |

**Принцип из спеки:** «не знаю канал — не храню» (`.claude/specs/channels-and-identity.md`).

Для перезаписываемого состояния (позиция ноды) достаточно transport slot — contour
резолвится при чтении через live `ChannelSlotResolver` + `primaryContourId`.

Для истории (сообщения, метки) — обязателен `contourId` на момент получения
(контур мог быть удалён / деактивирован позже).

---

## Scope

### In scope

- `ResolveContourFromSlotUseCase` + `ContourResolution` + `DeliveryPolicy`
- `TransportSlot` value class
- Рефакторинг 4 ingest/observe use case'ов на единый резолвер
- `ResolveContourFromSlotUseCaseTest` — полный набор кейсов (заменяет дубли в 4 test-файлах)
- Документация в `.claude/docs/` после реализации

### In scope (optional, phase 2)

- `MeshContactKey(slot, nodeId)` value class в mesh-слое (замена строкового `contactKey`)
- `telemetryChannel: Int?` в `NodeEntity` + маппинг в `MeshNodeModel`
- Убрать fallback `slotToHash miss → show` в observe use case'ах (сейчас inconsistent:
  `ObserveNodeMarkersUseCase` → `false`, `ObserveContourNodesUseCase` → `true`)

### Out of scope

- PKC channel (slot 8) полная поддержка в UI
- signal-tag parsing для Emergency вне SOS
- Миграция `contactKey` в Room packets table (оставить как есть, парсить на границе)
- QR/import sharing контуров
- `geo_mark_event` history table

---

## Implementation Phases

### Phase 1 — Domain models + resolver

**Файлы (new):**
- `domain/channel/model/TransportSlot.kt`
- `domain/channel/model/ContourResolution.kt`
- `domain/channel/model/DeliveryPolicy.kt`
- `domain/channel/usecase/ResolveContourFromSlotUseCase.kt`
- `domain/channel/usecase/ApplyDeliveryPolicyUseCase.kt` (или extension в том же файле)

**Файлы (test):**
- `test/.../ResolveContourFromSlotUseCaseTest.kt`

**Кейсы тестов:**
- slot 0 → primary contour
- slot 0, primary not found → Drop
- slot 1, SOS on → Deliver Emergency
- slot 1, SOS off → SilentStore Emergency
- slot N, hash found, isActive → Deliver
- slot N, hash found, isActive=false → Drop
- slot N, hash not in maps → Drop
- slot N, hash found, no matching contour → Drop

**DI:** register in `MeshDataModule` / `UserSettingsModule`

### Phase 2 — Refactor ingest use cases

**`IngestReceivedChatMessagesUseCase`:**
- Убрать inline `when (channelIndex)`
- Вызвать `resolveContourFromSlot`
- **Gap:** slot 1 сейчас попадает в `else` → hash lookup без SOS-gate. После рефактора slot 1 получит `SilentStore` когда `!sosMode`.
- `SilentStore` → insert без уведомлений (уже частично в adapter)
- `Drop` → skip
- Добавить `channelRepository.observeSosMode()` в combine (сейчас отсутствует)

**`IngestReceivedGeoMarksUseCase`:**
- Убрать inline `when (packet.channel)`
- `SilentStore` / `Drop` для Emergency вне SOS → drop (geo не храним silently)
- `Deliver` → `persistReceived(model, contour.id)`

**Verification:** существующие тесты `IngestReceivedChatMessagesUseCaseTest`,
`IngestReceivedGeoMarksUseCaseTest` должны проходить без изменения поведения.

### Phase 3 — ObserveContourNodesUseCase: slot 0 name lookup

**Статус пересмотрен.** Фильтрация нужна — пользователь видит ноды из Emergency на карте и в списке, чего не должно быть вне SOS-режима.

Обоснование «intentionally absent» в KDoc **ошибочно**: `receivedOnSlot` — это наш локальный слот, он однозначно идентифицирует PSK-канал. Не важно, каким слотом это было у отправителя — мы приняли пакет на slot 1, значит нода в Emergency.

**Фильтр для `ObserveNodeMarkersUseCase` и `ObserveGeoNodesUseCase`:**

```kotlin
// node visible iff receivedOnSlot passes contour filter
fun ContourResolution.allowsDisplay(): Boolean = when (this) {
    is ContourResolution.Deliver -> true
    is ContourResolution.SilentStore -> false  // Emergency, SOS off
    is ContourResolution.Drop -> false
}
```

Т.е.:
- `receivedOnSlot == null` → скрыть (нет position-пакета вообще)
- slot 0 → Primary → показать
- slot 1, `!sosMode` → SilentStore → скрыть
- slot 1, `sosMode` → Deliver → показать
- slot N, active contour → показать
- slot N, inactive / unknown → скрыть

**Изменения в use cases:**

`ObserveNodeMarkersUseCase` — добавить в combine `ContourRepository.observeContours()`, `observePrimaryContourId()`, `channelSlotResolver.mapsFlow`, `observeSosMode()`. Фильтровать `visible` через `ResolveContourFromSlotUseCase`.

`ObserveGeoNodesUseCase` — аналогично.

`ObserveContourNodesUseCase` — фильтрация + slot 0 name lookup fix (был gap: `slotToHash[0]` → null).

Удалить KDoc-комментарии с неверным обоснованием из всех трёх файлов.

### Phase 4 — MeshContactKey (optional)

**`mesh/model/MeshContactKey.kt`:**
```kotlin
@JvmInline value class MeshContactKey(val raw: String) {
    val slot: Int get() = ...
    val nodeId: String get() = ...
    companion object {
        fun broadcast(slot: Int) = MeshContactKey("$slot${DataPacket.ID_BROADCAST}")
        fun direct(slot: Int, nodeId: String) = MeshContactKey("$slot$nodeId")
    }
}
```

**`MeshDataHandlerImpl.rememberDataPacket`:** использовать `MeshContactKey` вместо конкатенации.

### Phase 5 — Telemetry channel (optional)

**`NodeEntity`:** добавить `telemetry_channel: Int?`
**`NodeManagerImpl.handleReceivedTelemetry`:** сохранять `packet.channel`
**`MeshNodeModel`:** `receivedTelemetryOnSlot: Int?`
**`ObserveContourNodesUseCase`:** фильтр телеметрии по контуру

DB migration в mesh Room (version bump).

### Phase 6 — Documentation

- Обновить `.claude/docs/contours.md` — секция Routing: ссылка на `ResolveContourFromSlotUseCase`
- Создать `.claude/docs/packet-channel-attribution.md` — operational guide
- Обновить `CLAUDE.md` docs table

---

## Текущие файлы (reference)

### Транспорт — где channel попадает в DataPacket

| Файл | Что делает |
|---|---|
| `mesh/model/util/MeshDataMapper.kt` | `channel = packet.channel` (PKC → 8) |
| `mesh/data/manager/MeshDataHandlerImpl.kt` | `contactKey`, position, text, nodeinfo handlers |
| `mesh/data/manager/MeshMessageProcessorImpl.kt` | `updateNode(..., channel = packet.channel)` |
| `mesh/data/manager/NodeManagerImpl.kt` | `positionChannel = channel` при POSITION |

### Domain — где резолвится сейчас (дубли)

| Файл | Паттерн |
|---|---|
| `IngestReceivedChatMessagesUseCase.kt` | contactKey → slot → contour |
| `IngestReceivedGeoMarksUseCase.kt` | packet.channel → contour |
| `ObserveNodeMarkersUseCase.kt` | receivedOnSlot → filter |
| `ObserveGeoNodesUseCase.kt` | receivedOnSlot → filter |
| `ObserveContourNodesUseCase.kt` | receivedOnSlot → filter + contourName |

### Инфраструктура (готова, не трогать)

| Файл | Роль |
|---|---|
| `ChannelSlotResolver` / `ChannelSlotResolverImpl` | live slot↔hash |
| `ContourRepository` | contours + primaryId + sosMode |
| `ResolveChannelSlotUseCase` | исходящий: hash → slot (другая задача) |

---

## Риски и митигации

| Риск | Митигация |
|---|---|
| Primary сменился — старые slot-0 пакеты «чужого» контура | Для истории храним `contourId` на момент получения; для позиций — live резолв (осознанный trade-off) |
| Нода переподключена, слоты переставлены | `ChannelSlotResolver` live; история по `contourId` стабильна |
| Расхождение правил после рефактора | Единый `ResolveContourFromSlotUseCaseTest` как contract test |
| `node.channel` путают с `positionChannel` | Документировать; в domain только `receivedOnSlot` / `receivedTelemetryOnSlot` |
| Emergency silent vs drop | Явный `DeliveryPolicy` per PortNum, не смешивать с резолвом |

---

## Acceptance Criteria

- [x] `ResolveContourFromSlotUseCase` — единственное место с `when (slot)` для slot→contour
- [x] `IngestReceivedChatMessagesUseCase` и `IngestReceivedGeoMarksUseCase` делегируют резолв
- [x] `IngestReceivedChatMessagesUseCase` обрабатывает slot 1 с SOS-gate (SilentStore / Drop)
- [x] `ObserveNodeMarkersUseCase`, `ObserveGeoNodesUseCase`, `ObserveContourNodesUseCase` фильтруют ноды через `ResolveContourFromSlotUseCase`
- [x] Emergency-ноды (slot 1) скрыты на карте и в списке вне SOS-режима
- [x] Все существующие unit-тесты чата, geo, node markers проходят
- [x] `ResolveContourFromSlotUseCaseTest` покрывает ≥ 8 кейсов (см. Phase 1)
- [x] Поведение изоляции из `.claude/docs/contours.md` не изменилось
- [x] Документация обновлена

---

## Open Questions

| Вопрос | Предварительное решение |
|---|---|
| PKC slot 8 — как резолвить? | Отдельный `ContourResolution` или always Drop до реализации PKC UI |
| SilentStore для Emergency chat — хранить в SQLDelight? | Да, но без unread/notification; или не хранить вовсе (решить в Phase 2) |
| `node.channel` deprecated? | Не удалять (mesh upstream), но domain не использовать |
| Fallback `receivedOnSlot=null` | `false` (не показывать) — уже в `ObserveNodeMarkersUseCase` |
