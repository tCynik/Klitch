# positionChannel и обнаружение слота ноды

**Date**: 2026-06-05
**Status**: Done

---

## Проблема

Ноды на карте должны фильтроваться по активному контуру (слоту). Для фильтрации `ObserveNodeMarkersUseCase` использует `MeshNodeModel.receivedOnSlot: Int?`, который отражает номер канала (слота), на котором была получена последняя позиция от данной ноды.

Значение `receivedOnSlot` передаётся из модели `Node.positionChannel: Int?`.

---

## Почему `NodeInfo.channel` нельзя использовать как слот

`NodeInfo.channel` (из Meshtastic protobuf) — это индекс канала **принимающей** радионоды, на котором она услышала данную ноду. Это поле не всегда совпадает с контурным слотом в рамках приложения:

- Firmware не гарантирует корректного маппинга между `channel` из NodeInfo и слотом контур-системы приложения
- Тестирование показало инверсию: ноды на слоте 0 отображались как слот 1 и наоборот
- Поле `NodeInfo.channel = 0` означает «услышан на канале 0» ИЛИ «не задано» (proto3 default) — семантически неотличимо

**Решение**: `positionChannel` устанавливается **исключительно** из живых position-пакетов (`MeshPacket.channel`), которые однозначно идентифицируют канал.

---

## Архитектура

### Поля и хранение

| Уровень | Поле | Тип | Хранение |
|---|---|---|---|
| `Node` (mesh domain) | `positionChannel: Int?` | `Int?` | только память до v3 DB |
| `NodeEntity` (Room) | `positionChannel: Int?` | `Int?` | колонка `position_channel`, default NULL |
| `MeshNodeModel` (app domain) | `receivedOnSlot: Int?` | `Int?` | не хранится (маппинг) |

### Как устанавливается `positionChannel`

**Единственный источник**: `handleReceivedPosition` в `NodeManagerImpl`:

```kotlin
node.copy(
    position = newPos,
    lastHeard = newLastHeard,
    channel = channel,           // channel = MeshPacket.channel
    positionChannel = channel,   // устанавливается из живого пакета
)
```

`channel` здесь — `MeshPacket.channel` от `MeshMessageProcessorImpl`, который правильно отражает слот контур-системы.

**`installNodeInfo` НЕ устанавливает `positionChannel`** (убрана строка `?: info.channel`):

```kotlin
// installNodeInfo — только сохраняет уже существующее значение
positionChannel = next.positionChannel,
```

### Персистирование

`positionChannel` включён в:
- `Node.toEntity()` — при `upsert` (вызывается после `isNodeDbReady = true`)
- `NodeEntity.toModel()` — при загрузке plain entity
- `NodeWithRelations.toModel()` — при загрузке через `getNodesFlow()` (основной путь `observeNodes()`)
- `installConfig()` — при первичной записи после BLE-синка

Room DB version: **3** (с v3 добавлена колонка; `fallbackToDestructiveMigration()` → DB сбрасывается при обновлении).

### Путь данных в фильтр

```
MeshPacket.channel
  → handleReceivedPosition(..., channel)
  → Node.positionChannel = channel
  → upsert → NodeEntity.positionChannel
  → getNodesFlow() → NodeWithRelations.toModel()
  → Node.positionChannel
  → NodeMapper.toMeshNodeModel()
  → MeshNodeModel.receivedOnSlot
  → passesContourFilter(receivedOnSlot, ...)
```

### Логика фильтра

`passesContourFilter` в `ObserveNodeMarkersUseCase`, `ObserveContourNodesUseCase`, `ObserveGeoNodesUseCase`:

```kotlin
when (receivedOnSlot) {
    null -> false         // слот неизвестен — скрыть
    0    -> true          // Primary — всегда видим
    1    -> sosMode       // Emergency — только в SOS-режиме
    else -> {
        val hash = maps.slotToHash[receivedOnSlot] ?: return true
        contourByHash[hash]?.isActive ?: true
    }
}
```

---

## Обнаружение слота при подключении

**Проблема**: после сброса DB (`position_channel = NULL`) нода невидима пока не пришлёт живую позицию.

**Решение**: `OnConnectPositionSender.requestPositionsForUnknownSlots()`.

При каждом подключении к BLE-ноде, после отправки своей позиции:

```kotlin
private suspend fun requestPositionsForUnknownSlots(gpsLocation: GpsLocation) {
    val ourNodeId = meshNetworkRepository.observeOurNode().first()?.nodeId ?: return
    val nodes = meshNetworkRepository.observeNodes().first()
    val position = Position(latitude = gpsLocation.latitude, longitude = gpsLocation.longitude, altitude = 0)
    nodes
        .filter { it.receivedOnSlot == null && it.nodeId != ourNodeId }
        .forEach { node ->
            delay(POSITION_REQUEST_INTERVAL_MS)  // 500ms между запросами
            commandSender.requestPosition(node.num, position)
        }
}
```

`commandSender.requestPosition` отправляет `POSITION_APP` пакет с `want_response = true`. Нода-получатель обязана ответить своей позицией. Ответ приходит как живой position-пакет → `handleReceivedPosition` → `positionChannel` установлен → персистирован в DB.

**Канал маршрутизации**: `requestPosition` использует `nodeDBbyNodeNum[destNum]?.channel ?: 0` (NodeInfo channel) — для доставки запроса это корректно, даже если для слот-фильтрации `info.channel` ненадёжен.

**Rate limiting**: 500мс между запросами. Не влияет на нормальный трафик, не флудит меш.

---

## Жизненный цикл positionChannel

| Событие | positionChannel |
|---|---|
| Первый запуск / DB wipe | `null` для всех нод |
| BLE подключение → `installNodeInfo` | сохраняет существующее значение (не перезаписывает) |
| `requestPositionsForUnknownSlots` → ответ ноды | устанавливается из живого пакета |
| Живой position-broadcast от ноды | устанавливается из живого пакета |
| Следующая сессия (из DB) | корректное сохранённое значение |

После первого успешного обнаружения слот персистируется и работает корректно во всех последующих сессиях.

---

## Ключевые файлы

| Файл | Роль |
|---|---|
| `mesh/.../NodeManagerImpl.kt` | `handleReceivedPosition` — единственное место записи `positionChannel` |
| `mesh/.../entity/NodeEntity.kt` | `positionChannel: Int?` — Room-колонка + `toModel()` + `NodeWithRelations.toModel()` |
| `mesh/.../repository/NodeRepositoryImpl.kt` | `Node.toEntity()` включает `positionChannel` |
| `mesh/.../MeshtasticDatabase.kt` | `version = 3` |
| `app/.../mapper/NodeMapper.kt` | `receivedOnSlot = positionChannel` |
| `app/.../OnConnectPositionSender.kt` | `requestPositionsForUnknownSlots` при подключении |
| `app/.../usecase/ObserveNodeMarkersUseCase.kt` | `passesContourFilter` — логика фильтра по слоту |
