# Фильтрация позиций по контуру — Filter-at-Input

**Date**: 2026-06-07
**Status**: Done (заменяет предыдущую slot-discovery архитектуру)

---

## Терминология

| Термин | Определение |
|---|---|
| **ИНДЕКС** | Порядковый номер канала в списке радионоды. **Ненадёжен** — у разных устройств одинаковый PSK может стоять на разных позициях |
| **КАНАЛ** | Фактический источник пакета, определяется PSK. `MeshPacket.channel` = локальный индекс слота, чей PSK расшифровал пакет на нашей ноде |

---

## Почему индексы ненадёжны

`MeshPacket.channel` на принимающей стороне = индекс в локальном списке каналов, PSK которого расшифровал пакет. Если у двух устройств одинаковый PSK на разных позициях, отправитель шлёт на индексе 0, а получатель видит индекс 1.

Подтверждено логами:
```
Отправитель: sendPosition ch=0
Получатель:  Position dropped: channel=1 not in active contour
```

**Вывод**: использовать `MeshPacket.channel` как абсолютный номер канала нельзя. Но использовать его как ключ в локальном маппинге — можно и нужно.

---

## Архитектура Filter-at-Input

Фильтрация позиций происходит **на входе** — в момент получения пакета, до записи в БД.

### Гарантия аппаратного уровня

Если радио расшифровало пакет — нода знает наш PSK. Это membership check на уровне железа. Дополнительного криптографического контроля не нужно.

### Логика фильтра

```
MeshPacket.channel = N
  → slotToHash[N] = hash       ← маппинг из конфига нашей ноды
  → contourByHash[hash]        ← наш контур с этим PSK
  → contour.isEmergency → принять только если sosMode
  → contour.isActive    → принять если активен
  → иначе отбросить
```

### Ключевой класс: `ContourPositionChannelFilter`

`app/data/mesh/ContourPositionChannelFilter.kt`

Поддерживает реактивный `Set<Int>` активных слотов (`activeSlots`) через `combine` трёх потоков:
- `ContourRepository.observeContours()`
- `ContourRepository.observeSosMode()`
- `ChannelSlotResolver.mapsFlow`

При каждом изменении контуров/SOS/маппинга пересчитывает активные слоты через `slotToHash` (не `hashToSlot`).

```kotlin
maps.slotToHash.forEach { (slot, hash) ->
    val contour = contourByHash[hash] ?: return@forEach
    val active = if (contour.isEmergency) sosMode else contour.isActive
    if (active) add(slot)
}
```

Начальное состояние: `emptySet()` — до получения конфига ноды ничего не принимается.

### Место применения: `MeshDataHandlerImpl.handlePosition`

```kotlin
if (packet.from != myNodeNum && !positionChannelFilter.isChannelAccepted(packet.channel)) {
    // drop — channel not in active contour
    return
}
```

Собственные пакеты (own node) принимаются всегда.

---

## Что убрано

### `requestPositionsOnConnect` — удалён

Ранее `OnConnectPositionSender` при подключении отправлял запросы позиций всем нодам с `receivedOnSlot == null` для обнаружения их слота. Механизм удалён:

- Не нужен: filter-at-input принимает позиции независимо от того, знаем ли мы слот ноды заранее
- `positionChannel` устанавливается из первого же принятого пакета позиции
- `requestPositionsForUnknownSlots` / `isWithinQueryWindow` / `shouldRequestPosition` — удалены из `OnConnectPositionSender`

### slot discovery из `handleReceivedData` — удалён

Ранее любой входящий пакет устанавливал `positionChannel` если он был `null`. Убрано: `positionChannel` устанавливается только из принятых position-пакетов в `handleReceivedPosition`.

### positionChannel fallback из `handleReceivedUser` — удалён

Ранее `NodeManagerImpl.handleReceivedUser` устанавливал `positionChannel` из NodeInfo-пакетов. Убрано по той же причине.

---

## passesContourFilter в UseCases — удалён

`ObserveNodeMarkersUseCase`, `ObserveContourNodesUseCase`, `ObserveGeoNodesUseCase` больше не содержат `passesContourFilter`. Видимость нод определяется только staleness (свежестью позиции). Все декодированные пакеты уже прошли PSK-фильтр на уровне железа.

---

## Staleness как естественный механизм скрытия

Если нода уходит с активного канала → filter-at-input отбрасывает её пакеты → `positionTime` перестаёт обновляться → через `POSITION_FRESHNESS_SECONDS` маркер становится серым → через `MAX_POSITION_AGE_SECONDS` скрывается.

Это и есть поведение "нода ушла из нашего контура = для нас она отключилась".

---

## Ключевые файлы

| Файл | Роль |
|---|---|
| `mesh/repository/PositionChannelFilter.kt` | Интерфейс фильтра (mesh-модуль) |
| `app/data/mesh/ContourPositionChannelFilter.kt` | Реализация: `slotToHash` → активный контур |
| `mesh/data/manager/MeshDataHandlerImpl.kt` | Точка применения фильтра (`handlePosition`) |
| `mesh/data/manager/NodeManagerImpl.kt` | `handleReceivedPosition` — единственное место записи `positionChannel` |
| `app/data/mesh/OnConnectPositionSender.kt` | Отправка своей позиции при подключении (slot discovery удалён) |
| `app/domain/map/usecase/ObserveNodeMarkersUseCase.kt` | Staleness-фильтр без `passesContourFilter` |
