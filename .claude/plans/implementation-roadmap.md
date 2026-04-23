# MeshTactics — Roadmap реализации

**Date**: 2026-04-23
**Принцип**: [Приложение — источник истины. Нода — тупое радио.](../specs/channels-and-identity.md)

---

## Контекст

Все архитектурные решения зафиксированы в `.claude/specs/channels-and-identity.md`.
Этот документ описывает **порядок реализации** — что и после чего строить.

Ключевая зависимость: `LogicalChannel` и `AppUser` — центральные сущности.
Всё последующее использует их как основу.

---

## Фазы

### Фаза 1 — User & Channels Settings UI ✅ Done

**План**: [user-and-channels-settings.md](user-and-channels-settings.md)
**Блокировала**: Фазы 2, 3, 4

Реализовано:
- Domain-модели: `AppUser`, `LogicalChannel`, `LogicalChannelId`, `ChannelMetadata`, `TransportBinding`, `MeshtasticBinding`
- Repository интерфейсы + use cases: `AppUserRepository`, `LogicalChannelRepository` + CRUD
- Fake in-memory impl (`FakeAppUserRepository`, `FakeLogicalChannelRepository`)
- `UserSettingsViewModel` + `UserSettingsUiState` + `ChannelEditorState`
- UI: `UserTabContent` (профиль + список каналов + `ChannelEditorSheet`)
- Валидация PSK: inline в composable через `remember`, блокирует кнопку Сохранить
- Реальные репозитории: `AppUserRepositoryImpl` (DataStore), `LogicalChannelRepositoryImpl` (SQLDelight)
- DI: `userSettingsModule` — реальные impl подключены

> `shortName` — атрибут конкретной ноды, не пользователя. Настраивается в Node Settings.
> При смене ноды `displayName` остаётся, `shortName` меняется.

**Результат**: позывной и каналы сохраняются персистентно. Domain-фундамент готов.

---

### Фаза 2 — Node Provisioning ✅ Done

**План**: [node-provisioning.md](node-provisioning.md)
**Зависела от**: Фазы 1

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

**Реализовано**: `NodeProvisioningUseCase` в `domain/mesh/usecase/`, триггер в `MainViewModel` при событии Connected.

**Результат**: нода автоматически приводится в соответствие с настройками приложения при подключении.

---

### Фаза 3 — Геометки: receive path на SQLDelight

**Зависит от**: Фазы 1 ✅ (разблокирована)
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

**Зависит от**: Фазы 1 ✅ (разблокирована)
**Затрагивает**: `chat-feature-plan.md` (In Progress — пауза снята, Фаза 1 завершена)

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

Фаза 1: User & Channels UI          ✅ Done
    │
    ├──→ Фаза 2: Node Provisioning   ✅ Done
    ├──→ Фаза 3: Геометки receive path   ⬜ Ready
    └──→ Фаза 4: Чат data layer          ⬜ Ready
```

Фазы 3 и 4 разблокированы, независимы между собой.

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
- 2026-04-20: Фаза 2 реализована — NodeProvisioningUseCase, триггер в MainViewModel
- 2026-04-23: Фаза 1 завершена полностью — реальные репозитории (DataStore + SQLDelight) реализованы и подключены в DI; Фазы 3 и 4 разблокированы
