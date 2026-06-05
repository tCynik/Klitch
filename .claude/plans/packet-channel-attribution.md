# Plan: Packet Channel Attribution — явная привязка канала к входящим сущностям

**Date**: 2026-06-05
**Status**: Draft — анализ завершён, реализация не начата

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

### Текущее состояние

| Сущность | Как определяется канал сейчас | Где хранится |
|---|---|---|
| Сообщения | `contactKey = "${channel}${nodeId}"` → парсинг → резолв | `chat_message.logical_channel_id` |
| Геометки | `packet.channel` → `when(slot)` | `geo_mark` через `contour.id` |
| Позиция ноды | `MeshPacket.channel` при POSITION_APP | `nodes.position_channel` → `MeshNodeModel.receivedOnSlot` |
| Телеметрия | **не сохраняется** | `node.channel` = последний слот любого пакета |
| NODEINFO | `packet.channel` при получении | `nodes.channel` (перезаписывается) |

### Дублирование логики резолва

Одинаковый `when (slot)` скопирован в:

- `IngestReceivedChatMessagesUseCase`
- `IngestReceivedGeoMarksUseCase`
- `ObserveNodeMarkersUseCase`
- `ObserveGeoNodesUseCase` / `ObserveContourNodesUseCase`

Правила (из `.claude/docs/contours.md`):

```
slot 0  → primaryContourId (не через hash!)
slot 1  → Emergency (SOS-gated для доставки)
slot N  → ChannelSlotResolver.slotToHash[N] → ContourHash → Contour
```

### Хрупкости

- `contactKey` как строка `"0^all"` / `"2!ab1234cd"` — парсится через `first().digitToInt()`
- `node.channel` ≠ `node.positionChannel` — разная семантика, легко перепутать
- Нет единого типа `PacketAttribution` — каждый ingest сам решает drop/deliver
- Телеметрия и события не привязаны к контуру

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
- `SilentStore` → insert без уведомлений (уже частично в adapter)
- `Drop` → skip

**`IngestReceivedGeoMarksUseCase`:**
- Убрать inline `when (packet.channel)`
- `SilentStore` / `Drop` для Emergency вне SOS → drop (geo не храним silently)
- `Deliver` → `persistReceived(model, contour.id)`

**Verification:** существующие тесты `IngestReceivedChatMessagesUseCaseTest`,
`IngestReceivedGeoMarksUseCaseTest` должны проходить без изменения поведения.

### Phase 3 — Refactor observe use cases

**`ObserveNodeMarkersUseCase`, `ObserveGeoNodesUseCase`, `ObserveContourNodesUseCase`:**
- Вынести `passesContourFilter()` в shared helper или метод резолвера:
  `fun shouldShowOnMap(slot: Int?, ...): Boolean`
- Унифицировать fallback при `slotToHash miss` → **false** (не показывать неизвестное)
- Убрать дублирование `passesContourFilter` (3 копии идентичного кода)

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

- [ ] `ResolveContourFromSlotUseCase` — единственное место с `when (slot)` для slot→contour
- [ ] 4 ingest/observe use case'а делегируют резолв, не дублируют
- [ ] Все существующие unit-тесты чата, geo, node markers проходят
- [ ] `ResolveContourFromSlotUseCaseTest` покрывает ≥ 8 кейсов (см. Phase 1)
- [ ] Поведение изоляции из `.claude/docs/contours.md` не изменилось
- [ ] Документация обновлена

---

## Open Questions

| Вопрос | Предварительное решение |
|---|---|
| PKC slot 8 — как резолвить? | Отдельный `ContourResolution` или always Drop до реализации PKC UI |
| SilentStore для Emergency chat — хранить в SQLDelight? | Да, но без unread/notification; или не хранить вовсе (решить в Phase 2) |
| `node.channel` deprecated? | Не удалять (mesh upstream), но domain не использовать |
| Fallback `receivedOnSlot=null` | `false` (не показывать) — уже в `ObserveNodeMarkersUseCase` |
