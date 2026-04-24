# Plan: Contour Concept (Контур)

**Date**: 2026-04-24
**Status**: Approved

## Summary

Переименовать "Логический канал" (LogicalChannel) → "Контур" (Contour) на всех слоях.
Ввести два вида Контура через бизнес-правила (без DB-колонки типа):

- **Primary** — дефолтный экстренный, maps to Meshtastic slot 0. Нельзя удалить, нельзя
  редактировать учётные данные канала. Геолокация запрещена: app-level (нет POSITION_APP
  на channel=0) + node-level (PositionConfig AdminMessage при каждом подключении).
- **Custom** — пользовательский (заблокирован в MVP).

Разграничение: Primary = row с `id == DefaultContour.ID` (hardcoded UUID). Нода этого
не знает. Пользователь это не видит.

---

## Scope

**In scope:**
- Rename sweep: `LogicalChannel*` → `Contour*` (domain, data, DI, tests, SQL)
- DB migration `5.sqm`: rename table `logical_channel` → `contour`
- `DefaultContour` object: fixed UUID + DISPLAY_NAME + TODO comments
- `ContourRepository.seedDefaultIfAbsent()` — seed on first run
- `ChannelSlotResolver`: slot 0 → `DefaultContour.ID` (bypass hash lookup)
- Routing special-case: incoming packet slot=0 → `DefaultContour.ID`
- `ChannelSyncStatus` для Primary: always `OnNode(0)` (no hash check)
- App-level geo protection: guard in position send path — no `POSITION_APP` on channel=0
- Node-level geo protection: `AdminMessage(set_config = PositionConfig(broadcast_secs=MAX))`
  отправляется при каждом `MeshConnectionStatus.Connected`
- UI: скрыть delete + edit для Primary Контура; скрыть checkbox auto-sync для Primary
- UI: скрыть "+ Добавить контур" (MVP блок)
- UI strings: "Каналы" → "Контуры" в секции настроек
- Tests: rename классы, добавить тесты seeding + geo protection + slot-0 routing

**Out of scope:**
- Создание новых Контуров (MVP block)
- Редактирование учётных данных Primary Контура (MVP block)
- SOS-режим (TODO only)
- Шаринг Контуров через QR/import (TODO only)
- DROP COLUMN meshtastic_slot (deferred: minSdk ≥ 35)

---

## Architecture Notes

### DefaultContour object

```kotlin
// domain/channel/model/DefaultContour.kt
object DefaultContour {
    val ID = ContourId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    const val DISPLAY_NAME = "Экстренный"
    // TODO(contour): replace hardcoded seed with contour sharing (QR/import)
}
```

Primary Контур DB row (seeded, never deleted):
```
id              = "00000000-0000-0000-0000-000000000001"
name            = "Экстренный"
meshtastic_psk  = null       — нет PSK: slot 0 всегда привязан имплицитно
channel_hash    = null       — нет hash: routing special-cased в коде
is_auto_sync    = 1          — на каждом connect: отправить PositionConfig disable
```

Seeded в `ContourRepositoryImpl.init {}` (или на первом `observeContours()`):
```kotlin
if (queries.selectById(DefaultContour.ID.value).executeAsOneOrNull() == null) {
    queries.upsert(
        id          = DefaultContour.ID.value,
        name        = DefaultContour.DISPLAY_NAME,
        psk         = null,
        hash        = null,
        isAutoSync  = 1L,
    )
}
```

Mapper: если `meshtastic_psk = null` → `transports = emptyList()`. Существующий
backfill-код пропускает строки с `psk = null` при вычислении хэша.

### Slot 0 Routing

Все пути routing (incoming packets) до hash-lookup:

```kotlin
// MeshToChatAdapter + IngestReceivedGeoMarksUseCase
val contourId = when (packet.channel) {
    0    -> DefaultContour.ID
    else -> resolver.slotToHash[packet.channel]
                ?.let { hash -> contourByHash[hash] }
                ?: run { Log.w(TAG, "unknown slot ${packet.channel}, drop"); return@collect }
}
```

`ChannelSlotResolverImpl` не трогаем — slot 0 там по-прежнему вычисляет hash из
node data ("LongFast", AQ==), но этот hash нигде не используется для Primary.

### ChannelSyncStatus для Primary

В `UserSettingsViewModel.computeSyncStatus()`:
```kotlin
if (channel.id == DefaultContour.ID) return ChannelSyncStatus.OnNode(0)
// ... existing hash-based logic для Custom контуров
```

### Geo Protection — App-level

Цель: приложение не отправляет `POSITION_APP` DataPacket с `channel = 0`.

Нужен research-шаг: найти где в `GpsService` / mesh-слое устанавливается `channel`
для position-пакета. Guard добавляется там.

### Geo Protection — Node-level

Новый метод в `CommandSender` (interface + impl):
```kotlin
fun disableNodePositionBroadcast()
// AdminMessage(set_config = Config(position = PositionConfig(position_broadcast_secs = UInt.MAX_VALUE)))
```

Нужен research-шаг: подтвердить наличие `set_config` в `AdminMessage` proto и
поля `position_broadcast_secs` в `PositionConfig` в этом codebase.

Trigger в `UserSettingsViewModel`, блок `combine(channels, nodeChannels, status)`:
```kotlin
if (status is MeshConnectionStatus.Connected) {
    disableNodePositionBroadcastUseCase()   // ← новое
    // ... existing auto-sync channels logic
}
```

### ContourItem (ранее ChannelItem)

```kotlin
data class ContourItem(
    val id: ContourId,
    val name: String,
    val isDefault: Boolean,          // id == DefaultContour.ID
    val isAutoSync: Boolean,
    val syncStatus: ChannelSyncStatus,
)
```

`isDefault` используется в UI: скрыть delete, edit, auto-sync checkbox.

---

## Phase Plan

### Phase 1 — Rename Sweep

**Goal**: проект компилируется, все `LogicalChannel*` → `Contour*`.

**Skill**: direct coding (механический rename)

**Файлы domain (rename + класс внутри):**
1. `domain/channel/model/LogicalChannel.kt` → `Contour.kt` (class `Contour`)
2. `domain/channel/model/LogicalChannelId.kt` → `ContourId.kt`
3. `domain/channel/model/LogicalChannelHash.kt` → `ContourHash.kt`
4. `domain/channel/repository/LogicalChannelRepository.kt` → `ContourRepository.kt`
5. `domain/channel/usecase/ObserveLogicalChannelsUseCase.kt` → `ObserveContoursUseCase.kt`
6. `domain/channel/usecase/SaveLogicalChannelUseCase.kt` → `SaveContourUseCase.kt`
7. `domain/channel/usecase/DeleteLogicalChannelUseCase.kt` → `DeleteContourUseCase.kt`

**Файлы data (rename + класс внутри):**
8. `data/channel/repository/LogicalChannelRepositoryImpl.kt` → `ContourRepositoryImpl.kt`
9. `data/channel/repository/FakeLogicalChannelRepository.kt` → `FakeContourRepository.kt`

**SQL:**
10. `LogicalChannel.sq` → `Contour.sq`; table `logical_channel` → `contour` внутри файла
11. Создать `5.sqm`:
    ```sql
    ALTER TABLE logical_channel RENAME TO contour;
    ```
    (SQLite сохраняет FK-ссылки при rename; chat_message + geo_mark FK не ломаются)

**Test файлы (rename + update imports):**
12. `LogicalChannelRepositoryImplTest.kt` → `ContourRepositoryImplTest.kt`
13. `LogicalChannelHashTest.kt` → `ContourHashTest.kt`

**References update (update imports в существующих файлах):**
14. `ChannelSlotResolverImpl.kt` — `LogicalChannelHash` → `ContourHash`
15. `MeshToChatAdapter.kt` — imports
16. `GeoMarkRepositoryImpl.kt` — imports
17. `UserSettingsViewModel.kt` — все ссылки (`LogicalChannelId` → `ContourId`, etc.)
18. `MainViewModel.kt` — все ссылки
19. `UserSettingsModule.kt` + все DI-модули — bindings
20. `PresentationModule.kt` — bindings
21. Presentation models: `ChannelItem.kt` → `ContourItem.kt` (add `isDefault: Boolean`)
22. `UserSettingsViewModelChannelsTest.kt` + `ChannelSlotResolverImplTest.kt` — imports

**String resources:**
23. Добавить `user_section_contours = "Контуры"` (старый `user_section_channels` убрать
    или оставить как alias — проверить usage)
24. `user_add_channel_button` — убрать (кнопка скрыта в MVP)

### Phase 2 — Default Контур

**Goal**: Primary Контур в DB, slot 0 routing работает, sync status корректный.

1. Новый файл `domain/channel/model/DefaultContour.kt` — object с ID + DISPLAY_NAME + TODO
2. Добавить `seedDefaultIfAbsent()` в `ContourRepository` interface
3. Реализовать в `ContourRepositoryImpl.init {}` — insert если отсутствует
4. Routing fix в `MeshToChatAdapter`: `when (packet.channel) { 0 -> DefaultContour.ID ... }`
5. Routing fix в `IngestReceivedGeoMarksUseCase`: то же самое
6. `UserSettingsViewModel.computeSyncStatus()`: Primary → `OnNode(0)` без hash check
7. `UserSettingsViewModel.onDeleteContourRequest()`: guard `if (id == DefaultContour.ID) return`
8. `UserSettingsViewModel`: compute `isDefault` в combine block → передать в `ContourItem`

### Phase 3 — Geo Protection

**Goal**: app не шлёт гео на ch=0; нода сконфигурирована не бродкастить позицию.

**Research (перед кодингом):**
- Прочитать `CommandSenderImpl.kt`: есть ли `AdminMessage(set_config = ...)` и поле
  `PositionConfig` / `position_broadcast_secs`
- Прочитать `GpsService.kt`: где `channel` для position-пакета устанавливается

**App-level guard:**
1. Добавить guard в position send path: пропускать если `channel == 0`

**Node-level:**
2. `CommandSender` interface: добавить `fun disableNodePositionBroadcast()`
3. `CommandSenderImpl`: реализовать через `sendAdmin(AdminMessage(set_config = ...))`
4. Новый `domain/mesh/usecase/DisableNodePositionBroadcastUseCase.kt`
5. `UserSettingsViewModel`: вызов `disableNodePositionBroadcastUseCase()` при `Connected`
6. DI: добавить `DisableNodePositionBroadcastUseCase` в `UserSettingsModule`

### Phase 4 — UI Restrictions

**Goal**: Primary Контур защищён в UI; создание новых заблокировано.

1. `ContourCard` (ранее `ChannelCard`): принять `isDefault: Boolean`
   - `isDefault = true`: скрыть кнопку delete из dropdown
   - `isDefault = true`: скрыть пункт "Редактировать" из dropdown
   - `isDefault = true`: скрыть auto-sync checkbox
2. `UserTabContent`: кнопку "+ Добавить контур" оставить, но `enabled = false`
   ```kotlin
   // TODO(contour): разблокировать после реализации шаринга контуров (QR/import)
   ```
3. Переименовать заголовок секции: `user_section_channels` → `user_section_contours`

### Phase 5 — Tests

1. Обновить imports в переименованных тест-классах (Phase 1 tail)
2. `ContourRepositoryImplTest`:
   - DB пуста → `seedDefaultIfAbsent()` insert Primary Контур
   - DB уже имеет Primary → второй вызов не дублирует строку
3. Routing tests (новые или расширение `MeshToChatAdapterTest`):
   - Пакет с `channel=0` → `contourId = DefaultContour.ID`
   - Пакет с неизвестным слотом → dropped (Log.w)
4. Geo protection tests:
   - App-level: `DataPacket` с `channel=0` не создаётся / не отправляется
   - Node-level: на `Connected` → `disableNodePositionBroadcast()` вызван

### Phase 6 — Docs & Memory

1. Обновить `.claude/docs/logical-channels-management.md` in-place:
   переименовать concept, отразить DefaultContour, routing slot 0, geo protection
2. Обновить `CLAUDE.md`: строка "Логические каналы" → "Контуры", статус
3. Обновить `CLAUDE.md` активные планы: убрать устаревшую строку про contour-management.md
4. Архивировать если нужно

---

## Coordination Map

```
Phase 1: [direct coding — механический rename] → компиляция проверена
Phase 2: [direct coding — seeding + routing]
Phase 3: [research step] → [direct coding — geo protection]
Phase 4: [direct coding — UI]
Phase 5: [direct coding — tests]
Phase 6: [docs update]
```

---

## TODO comments to add in code

```kotlin
// TODO(contour): replace hardcoded DefaultContour seed with contour sharing (QR/import)
// TODO(contour): unblock Custom Контур creation after sharing is implemented
// TODO(contour): SOS mode — enable geo + message send on Primary channel on demand
// TODO(contour): unblock ContourEditorSheet for Primary when SOS config UI is designed
```

---

## Change Log

- 2026-04-24: создан v1 — rename sweep + Primary Контур + geo protection
