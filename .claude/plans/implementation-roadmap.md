# MeshTactics — Roadmap реализации

**Date**: 2026-04-20
**Принцип**: [Приложение — источник истины. Нода — тупое радио.](../specs/channels-and-identity.md)

---

## Контекст

Все архитектурные решения зафиксированы в `.claude/specs/channels-and-identity.md`.
Этот документ описывает **порядок реализации** — что и после чего строить.

Ключевая зависимость: `LogicalChannel` и `AppUser` — центральные сущности.
Всё последующее использует их как основу.

---

## Фазы

### Фаза 1 — User & Channels Settings UI

**План**: [user-and-channels-settings.md](user-and-channels-settings.md)
**Блокирует**: Фазы 2, 3, 4

Строим вкладку **Пользователь** в настройках:
- Domain-модели: `AppUser` (только `displayName`), `LogicalChannel`, `LogicalChannelId`, `ChannelMetadata`, `TransportBinding`, `MeshtasticBinding`
- Repository интерфейсы: `AppUserRepository`, `LogicalChannelRepository`
- UI: профиль пользователя (позывной) + список каналов + редактор канала
- Fake in-memory impl → реальное хранилище (DataStore + SQLDelight)

> `shortName` — атрибут конкретной ноды, не пользователя. Настраивается в Node Settings.
> При смене ноды `displayName` остаётся, `shortName` меняется.

**Результат**: пользователь может настроить позывной и каналы. Domain-фундамент готов.

---

### Фаза 2 — Node Provisioning

**План**: требует создания
**Зависит от**: Фазы 1

При подключении ноды к приложению:

```
1. Читаем каналы ноды
2. Сравниваем с LogicalChannel в приложении
3. При расхождении — предлагаем синхронизировать
4. Записываем каналы на ноду
5. Очищаем лишние слоты (опционально — с разрешения пользователя)
6. Пушим AppUser.displayName → LongName ноды
7. Пушим NodeSettings.shortName → ShortName ноды  (shortName — атрибут ноды, не AppUser)
```

Ключевой use case: `NodeProvisioningUseCase`.

> **TODO (из спека):** при обнаружении каналов на ноде, отсутствующих в приложении,
> предложить: импортировать / игнорировать / очистить слот.
> До реализации этого флоу — молча фильтруем.

**Результат**: нода автоматически приводится в соответствие с настройками приложения при подключении.

---

### Фаза 3 — Геометки: receive path на SQLDelight

**Зависит от**: Фазы 1
**Затрагивает**: `GeoMarkRepositoryImpl`, `GeoMark.sq`

Текущая проблема: receive path читает геометки из Room DB ноды (`PacketRepository.getWaypoints()`).
После рефакторинга нода — только источник событий.

Изменения:

```
до:   PacketRepository.getWaypoints() + selectSelfIds → combine → decode → UI
после: PacketRepository.getWaypoints() → decode → INSERT в SQLDelight (если не истёк TTL)
       ObserveGeoMarksUseCase читает ТОЛЬКО из SQLDelight
```

Правила вставки:
- `channelIndex` → `LogicalChannelId` (через `MeshtasticBinding`); если канал неизвестен — отбросить
- `expires_at <= now` — отбросить; `expire = 0` → `expires_at = NULL` (бессрочно)
- `INSERT OR IGNORE` по `geo_mark.id` (защита от повторной доставки)

Добавить в схему `GeoMark.sq`:
```sql
logical_channel_id TEXT NOT NULL
```

Добавить запрос `selectAllForChannel(logicalChannelId)`.
Периодический вызов `deleteExpired` — при старте и по таймеру.

**Результат**: история геометок переживает смену ноды. Геометки из неизвестных каналов не хранятся.

---

### Фаза 4 — Чат: data layer на SQLDelight с LogicalChannelId

**Зависит от**: Фазы 1
**Затрагивает**: `chat-feature-plan.md` (In Progress — пауза на data/domain до завершения Фазы 1)

Текущая проблема: чат читает сообщения из Room DB Meshtastic-слоя.

После рефакторинга:

```
пакет TEXT_MESSAGE_APP прибыл
  → MeshToChatAdapter декодирует
  → channelIndex → LogicalChannelId
  → INSERT в chat_message (SQLDelight) с logical_channel_id
  → ObserveMessagesUseCase читает из SQLDelight (фильтр по LogicalChannelId)
```

Новая таблица:
```sql
CREATE TABLE chat_message (
    id                 TEXT NOT NULL PRIMARY KEY,  -- message id из Meshtastic
    logical_channel_id TEXT NOT NULL,
    sender_node_id     TEXT NOT NULL,
    text               TEXT NOT NULL,
    sent_at            INTEGER NOT NULL,           -- Unix seconds
    is_self            INTEGER NOT NULL DEFAULT 0
);
```

Также заменить `ContactType.CHANNEL` / `ChatContact.id` на `LogicalChannelId` (см. спек).

**Результат**: история чата хранится в приложении, привязана к логическому каналу, переживает смену ноды.

---

## Параллельный трек: Settings Refactor

**План**: [settings-refactor.md](settings-refactor.md)
**Зависимости**: нет, не пересекается с Фазами 1–4

Узкий scope: Clean Architecture для настроек маркеров.
Можно выполнить до или параллельно с Фазой 1.

---

## Граф зависимостей

```
Settings Refactor ──────────────────────────────────── (параллельно)

Фаза 1: User & Channels UI
    │
    ├──→ Фаза 2: Node Provisioning
    ├──→ Фаза 3: Геометки receive path
    └──→ Фаза 4: Чат data layer
```

Фазы 2, 3, 4 независимы между собой — могут выполняться в любом порядке после Фазы 1.

---

## За рамками roadmap (дальше по развитию)

- Channel-aware позиции участников на карте
- Channel-aware телеметрия нод
- История обмена геометками (`geo_mark_event`, отдельная таблица без TTL)
- Управление приоритетом транспортов (WiFi vs Meshtastic)
- Многоканальный чат (переключение между каналами в одном экране)

---

## Change Log

- 2026-04-20: создан
- 2026-04-20: уточнено — `shortName` атрибут ноды, не `AppUser`; в Node Provisioning берётся из NodeSettings
