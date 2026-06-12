# Packet Channel Attribution

Операционный guide по привязке транспортного слота Meshtastic к логическому контуру приложения.

## Два уровня идентичности

| Уровень | Тип | Источник |
|---|---|---|
| Transport | `Int` 0–7 (8 = PKC DM) | `MeshPacket.channel` / `DataPacket.channel` |
| Domain | `ContourId` (UUID) | `ResolveContourFromSlotUseCase` |

Канал **никогда** не выводится из `from`/`to`, имени ноды или строкового `contactKey`. Единственный авторитетный транспортный сигнал — `packet.channel`.

## Цепочка обработки

```
MeshPacket прибыл
  → MeshDataMapper: DataPacket.channel = packet.channel
  → ResolveContourFromSlotUseCase → ContourResolution
  → ApplyDeliveryPolicyUseCase → DeliveryPolicy
  → INSERT/UPDATE или фильтр при observe
```

## ResolveContourFromSlotUseCase

Файл: `domain/channel/usecase/ResolveContourFromSlotUseCase.kt`

| Slot | Результат |
|---|---|
| 0 | `Deliver(primaryContour)` или `Drop` |
| 1 | `Deliver(Emergency)` если SOS; иначе `SilentStore(Emergency)` |
| 2–7 | hash lookup → `Deliver` если active; иначе `Drop` |
| 8 | `Drop` (PKC не поддержан) |

Extensions в `ContourResolution.kt`:
- `allowsDisplay()` — `Deliver` → true; `SilentStore`/`Drop` → false
- `contourOrNull()` — контур из `Deliver`/`SilentStore`

## DeliveryPolicy по типу пакета

`ApplyDeliveryPolicyUseCase` + `InboundPacketKind`:

| Packet kind | Deliver | SilentStore | Drop |
|---|---|---|---|
| TEXT_MESSAGE | UI + persist | persist, no notification | skip |
| WAYPOINT | persist | drop | drop |
| POSITION | show on map | drop | drop |
| TELEMETRY / NODEINFO | process | drop | drop |

## Хранение по сущности

| Сущность | При получении | При чтении |
|---|---|---|
| Сообщение | `logical_channel_id` = `contourId` | `WHERE logical_channel_id = ?` |
| Геометка | `contourId` | TTL + contour scope |
| Позиция ноды | `positionChannel` (transport slot) | `ResolveContourFromSlot` при observe |
| Телеметрия | — (Phase 5 planned) | — |

Принцип: **не знаю канал — не храню** (для истории). Для перезаписываемого состояния (позиция) достаточно transport slot — contour резолвится при чтении через live `ChannelSlotResolver`.

## Потребители

| Use case | Роль |
|---|---|
| `IngestReceivedChatMessagesUseCase` | ingest + SOS-gate для slot 1 |
| `IngestReceivedGeoMarksUseCase` | ingest; SilentStore → drop |
| `ObserveNodeMarkersUseCase` | contour filter на карте |
| `ObserveGeoNodesUseCase` | contour filter в списке гео-нод |
| `ObserveContourNodesUseCase` | filter + contour name (slot 0 через primary) |

## Тесты-контракт

`ResolveContourFromSlotUseCaseTest` — единственный contract test для правил slot→contour. Ingest/observe тесты делегируют резолв, не дублируют `when (slot)`.

## Out of scope (MVP)

- PKC slot 8 в UI
- signal-tag parsing для Emergency вне SOS
- `telemetryChannel` в NodeEntity — не реализуется: Network screen уже фильтрует ноды через `ObserveContourNodesUseCase` по `receivedOnSlot`
- `MeshContactKey` value class
