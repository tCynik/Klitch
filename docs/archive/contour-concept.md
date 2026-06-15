# Plan: Contour Concept (Контур)

**Date**: 2026-04-24
**Status**: Approved

## Summary

Переименовать "Логический канал" (LogicalChannel) → "Контур" (Contour) на всех слоях.
Ввести два специальных Контура:

- **Emergency** — экстренный. Hardcoded в коде, не хранится в DB. `isActive = false`
  по умолчанию. Занимает Meshtastic slot 0 (primary channel, open PSK). Нельзя удалить.
  Когда активен: геолокация запрещена (app-level + node-level PositionConfig).
  Имя контура: `"Emergency"`. Имя канала: `""` (стандартный Meshtastic primary).
- **Default** — рабочий из коробки. Обычная DB-строка, deletable. `isActive = true`
  по умолчанию. Seeded при первом запуске на первый свободный слот (slot 1+), open PSK.
  Имя контура: `"Default contour"`. Имя канала: `"default"`.
- **Custom** — пользовательский (заблокирован в MVP).

Emergency идентифицируется по `id == DefaultContour.ID` (hardcoded UUID `...0001`).
Default seeded с `id == DefaultActiveContour.ID` (UUID `...0002`), но после удаления
пользователем этот ID исчезает из DB — обычный контур.

Нода не знает об `isActive`. Пользователь видит имя и `isActive` toggle.

### isActive — семантика

`isActive` управляет участием контура в передаче и приёме сообщений и геопозиций:

| isActive | Входящие сообщения | Исходящие сообщения | Гео (приём) | Гео (отправка) |
|---|---|---|---|---|
| `true` | доставляются в UI | отправляются | обрабатываются | отправляются |
| `false` | игнорируются | блокируются | игнорируются | блокируются |

Фильтрация на app-уровне перед/после network-слоя. Нода не знает об `isActive`.

### Routing — один слот, один контур

Слот не хранится в контуре. При sync на connect приложение пишет контур на первый
свободный слот ноды. При получении пакета: `slot index → channelHash → Contour`.

```kotlin
// MeshToChatAdapter + IngestReceivedGeoMarksUseCase
val contour = when (packet.channel) {
    0    -> emergencyContour.takeIf { it.isActive }
    else -> {
        val hash = resolver.slotToHash[packet.channel]
        contours.find { it.transport.meshtastic.channelHash == hash }
            ?.takeIf { it.isActive }
    }
} ?: return@collect  // drop: неизвестный слот или контур неактивен
```

Emergency контур (slot 0) — специальный случай: не из DB, возвращается напрямую.

---

## Scope

**In scope:**
- Rename sweep: `LogicalChannel*` → `Contour*` (domain, data, DI, tests, SQL)
- DB migration `5.sqm`: rename + добавить колонки `is_active`, `description`, `expiration`, `exclusivity_time`
- `Contour` domain model: новая структура с `transport: ContourTransport` (non-null), `isActive`, `description`, `expiration`, `exclusivityTime`; удалить `ChannelMetadata`
- `ContourTransport(meshtastic: MeshtasticChannel)` — новая структура транспорта
- `DefaultContour` object: hardcoded singleton с транспортом (slot 0, open PSK), не в DB
- `DefaultActiveContour` object: UUID + константы для seeding (обычный DB-контур)
- `ContourRepository.seedDefaultsIfAbsent()` — seed только DefaultActive при первом запуске
- Routing: `slot → hash → contour`; slot 0 — Emergency hardcoded; без fan-out
- `isActive` filter: блокировать send/receive для неактивных контуров
- Sync on Connect: при `Connected` → push всех `isActive` контуров на ноду (первый свободный слот); TODO-лог если слотов нет
- `ChannelSyncStatus` для Emergency: always `OnNode(0)` без hash-check
- Geo Config on Connect: AdminMessage зависит от `isActive` Emergency
  - Emergency isActive=false → Geo ENABLED (Ready config)
  - Emergency isActive=true → Geo DISABLED (PositionConfig MAX)
- App-level geo protection: guard — нет POSITION_APP когда Emergency isActive
- Node-level geo protection/enable через `CommandSender`
- UI: скрыть delete + edit только для Emergency (`isEmergency`)
- UI: показать `isActive` toggle для каждого контура
- UI: скрыть "+ Добавить контур" (MVP блок)
- UI strings: "Каналы" → "Контуры"
- Tests: rename, seeding, isActive filter, routing, sync on connect, geo config

**Out of scope:**
- Создание новых Контуров (MVP block)
- SOS-режим (TODO only)
- Шаринг Контуров через QR/import (TODO only)
- Обработка "нет свободных слотов" при sync (TODO: лог)
- DROP COLUMN meshtastic_slot (deferred: minSdk ≥ 35)

---

## Architecture Notes

### ContourTransport структура

```kotlin
// domain/channel/model/ContourTransport.kt
data class ContourTransport(
    val meshtastic: MeshtasticChannel,
    // future: val satellite: SatelliteChannel? = null
)

// domain/channel/model/MeshtasticChannel.kt
data class MeshtasticChannel(
    val psk: String,
    val channelHash: ContourHash,
)
```

Транспорт — обязательная часть контура. Контур без транспорта не существует.
Генерируется автоматически при создании контура. Пользователь не может удалить транспорт,
только опосредованно изменить некоторые настройки.

### DefaultContour object (hardcoded, не в DB)

```kotlin
// domain/channel/model/DefaultContour.kt
object DefaultContour {
    val ID = ContourId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    const val DISPLAY_NAME = "Emergency"
    const val CHANNEL_NAME = ""           // стандартный Meshtastic primary channel
    const val OPEN_PSK = "AQ=="
    const val IS_ACTIVE = false

    val CHANNEL_HASH = ContourHash.compute(CHANNEL_NAME, OPEN_PSK)

    val TRANSPORT = ContourTransport(
        meshtastic = MeshtasticChannel(psk = OPEN_PSK, channelHash = CHANNEL_HASH)
    )

    fun asContour() = Contour(
        id = ID, name = DISPLAY_NAME, description = null,
        expiration = null, exclusivityTime = null,
        isActive = IS_ACTIVE, transport = TRANSPORT,
    )
    // TODO(contour): SOS mode — активировать автоматически при тревоге
}
```

Emergency контур не хранится в DB. `ContourRepositoryImpl` не seed'ит его.
При `observeContours()` он добавляется в список на уровне репозитория:
```kotlin
queries.selectAll().asFlow()...map { dbContours ->
    listOf(DefaultContour.asContour().copy(isActive = /* читаем из prefs или всегда false */)) + dbContours
}
```
`isActive` для Emergency хранится в `DataStore` (не в DB), т.к. строки в DB нет.

### DefaultActiveContour (seeded DB-контур)

```kotlin
// domain/channel/model/DefaultActiveContour.kt
object DefaultActiveContour {
    val ID = ContourId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
    const val DISPLAY_NAME = "Default contour"
    const val CHANNEL_NAME = "default"
    const val IS_ACTIVE = true
    // TODO(contour): replace hardcoded seed with contour sharing (QR/import)
}
```

Обычный DB-контур. Пользователь может удалить. После удаления не восстанавливается.
PSK генерируется при seed: `OPEN_PSK = "AQ=="` (open), channelHash вычисляется.

DB row (seeded once, deletable):
```
id               = "00000000-0000-0000-0000-000000000002"
name             = "Default contour"
description      = null
expiration       = null
exclusivity_time = null
meshtastic_psk   = "AQ=="
channel_hash     = ContourHash.compute("default", "AQ==")
is_active        = 1
```

Seeded в `ContourRepositoryImpl.init {}`:
```kotlin
fun seedDefaultsIfAbsent() {
    if (queries.selectById(DefaultActiveContour.ID.value).executeAsOneOrNull() == null) {
        val hash = ContourHash.compute(DefaultActiveContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
        queries.upsert(
            id = DefaultActiveContour.ID.value,
            name = DefaultActiveContour.DISPLAY_NAME,
            description = null, expiration = null, exclusivityTime = null,
            psk = DefaultContour.OPEN_PSK,
            hash = hash.value,
            isActive = 1L,
        )
    }
}
```

Mapper: `meshtastic_psk NOT NULL` → всегда создаёт `ContourTransport(MeshtasticChannel(...))`.
Старый backfill-код (psk=null guard) — удаляется.

### Routing — slot → contour

Каждый слот = один контур. Без fan-out. Слот не хранится в контуре — определяется
при sync на connect.

```kotlin
// MeshToChatAdapter + IngestReceivedGeoMarksUseCase
val contour: Contour? = when (packet.channel) {
    0    -> emergencyContour.takeIf { it.isActive }
    else -> {
        val hash = resolver.slotToHash[packet.channel]
        if (hash == null) { Log.w(TAG, "unknown slot ${packet.channel}, drop"); return@collect }
        val found = contours.find { it.transport.meshtastic.channelHash == hash }
        if (found == null) { Log.w(TAG, "no contour for hash $hash, drop"); return@collect }
        found.takeIf { it.isActive }
    }
} ?: return@collect  // drop: неизвестный слот или контур неактивен
```

`emergencyContour` — `DefaultContour.asContour()` с актуальным `isActive` из DataStore.

### Sync on Connect

При каждом `MeshConnectionStatus.Connected`:
1. Собрать все `isActive` контуры из DB + Emergency если `isActive`
2. Для каждого — проверить `ChannelSyncStatus` (hash совпадает с нодой?)
3. Если `OutOfSync` или `NotOnNode` → `sendAdmin(set_channel)` на первый свободный слот
4. Slot 0 всегда резервируется для Emergency (write slot 0 независимо от isActive)
5. Если свободных слотов нет → `Log.w(TAG, "no free slots")` TODO(contour): обработать

```kotlin
// новый use-case: SyncContoursOnConnectUseCase
suspend fun execute() {
    val nodeSlots: Map<Int, ContourHash> = resolver.slotToHash  // текущее состояние ноды
    val freeSlots = (0..7).filter { it !in nodeSlots || it == 0 }.toMutableList()
    // slot 0 — всегда для Emergency
    commandSender.setChannel(slot = 0, name = DefaultContour.CHANNEL_NAME, psk = DefaultContour.OPEN_PSK)
    freeSlots.remove(0)
    // остальные isActive контуры
    val activeContours = contourRepository.observeContours().first()
        .filter { it.isActive && !it.isEmergency }
    for (contour in activeContours) {
        val slot = freeSlots.removeFirstOrNull()
            ?: run { Log.w(TAG, "no free slots for ${contour.name}"); return }
        commandSender.setChannel(slot, contour.transport.meshtastic)
    }
}
```

`ChannelSlotResolverImpl` не трогаем — slot 0 там по-прежнему вычисляет hash из
node data ("LongFast", AQ==), но этот hash нигде не используется для hardcoded контуров.

### ChannelSyncStatus для hardcoded контуров

В `UserSettingsViewModel.computeSyncStatus()`:
```kotlin
if (contour.id == DefaultContour.ID || contour.id == DefaultActiveContour.ID)
    return ChannelSyncStatus.OnNode(0)
// ... existing hash-based logic для Custom контуров
```

### Geo Config on Connect

При каждом `MeshConnectionStatus.Connected` приложение определяет актуальный
geo-режим для slot 0 и отправляет соответствующий AdminMessage.

**Логика выбора режима:**
```
DefaultActiveContour.isActive == true  →  Geo ENABLED (Ready config)
DefaultActiveContour.isActive == false
  AND DefaultContour.isActive == true  →  Geo DISABLED (Max interval)
оба false                              →  не отправляем ничего (нет активных)
```

Geo ENABLED = `LocationSharingStatus.Ready` конфигурация:
- `position_broadcast_secs = 60`  (≤ 120 → нет Warning.BROADCAST_INTERVAL_HIGH)
- `gps_mode = NOT_PRESENT` (или DISABLED)  (не ENABLED → нет Warning.GPS_MODE_CONFLICT)
- `position_flags` ≠ 0 (не пустые → нет Warning.NO_POSITION_FLAGS)
- `fixed_position = false`  (не блокирует шаринг)

Дополнительно — precision на channel 0 (блокер `CHANNEL_PRECISION_DISABLED`):
- `ChannelSettings(position_precision = 13)` для channel index 0

Geo DISABLED = экстренный режим (Primary active):
- `position_broadcast_secs = UInt.MAX_VALUE`

**Новые методы в `CommandSender`:**
```kotlin
fun enableNodePositionBroadcastReady()
// AdminMessage(set_config = Config(position = PositionConfig(
//     position_broadcast_secs = 60,
//     gps_mode = GpsMode.NOT_PRESENT,
//     position_flags = DEFAULT_FLAGS  // TBD research step
// )))
// AdminMessage(set_channel = Channel(index=0, settings = ChannelSettings(position_precision=13)))

fun disableNodePositionBroadcast()
// AdminMessage(set_config = Config(position = PositionConfig(
//     position_broadcast_secs = UInt.MAX_VALUE
// )))
```

**Нужен research-шаг:** подтвердить наличие `set_config` + `set_channel` в `AdminMessage`
proto; поля `PositionConfig`, `ChannelSettings.position_precision` в этом codebase.

**App-level geo filter:**

Нужен research-шаг: найти где в `GpsService` / mesh-слое устанавливается `channel`
для position-пакета.

Guard: отправка POSITION_APP DataPacket разрешена только если отправляющий контур
`isActive == true` И это не Primary (или Primary `isActive = true` AND geo explicitly enabled).
Проще: просто проверяем `activeContourForSlot0.id != DefaultContour.ID`.

### Domain model (`Contour`)

```kotlin
data class Contour(
    val id: ContourId,
    val name: String,
    val description: String?,
    val expiration: Instant?,       // UI-only: показать что контур устарел
    val exclusivityTime: Instant?,  // UI-only: до этого момента контур требует эксклюзивности
    val isActive: Boolean,
    val transports: List<TransportBinding>,
)
```

`ChannelMetadata` обёртка — удаляется. `name` и `description` напрямую в `Contour`.

Extensions в domain (не хранятся):
```kotlin
val Contour.isEmergency: Boolean get() =
    id == DefaultContour.ID || id == DefaultActiveContour.ID

val Contour.isSlot0Contour(): Boolean get() =
    id == DefaultContour.ID || id == DefaultActiveContour.ID
```

`isEmergency` используется в UI-гардах (скрыть delete/edit) и для идентификации системных контуров.
Для geo-логики (различить именно Primary) — проверяем `id == DefaultContour.ID` напрямую в use case.

---

### ContourItem (ранее ChannelItem)

```kotlin
data class ContourItem(
    val id: ContourId,
    val name: String,
    val description: String?,
    val expiration: Instant?,
    val exclusivityTime: Instant?,
    val isActive: Boolean,
    val isEmergency: Boolean,       // вычислен в ViewModel
    val syncStatus: ChannelSyncStatus,
)
```

`isEmergency` используется в UI: скрыть delete, edit; убрать отдельный checkbox синхронизации.
`isActive` используется в UI: toggle switch на ContourCard.

`isEmergency` вычисляется в combine-блоке ViewModel:
```kotlin
val isEmergency = contour.isEmergency
```

---

## Phase Plan

### Phase 1 — Rename Sweep + новая модель

**Goal**: проект компилируется, все `LogicalChannel*` → `Contour*`; новая структура домена.

**Skill**: direct coding (механический rename + структурные изменения)

**Файлы domain:**
1. `domain/channel/model/LogicalChannel.kt` → `Contour.kt`
   — поля: `id`, `name`, `description?`, `expiration?`, `exclusivityTime?`, `isActive`, `transport: ContourTransport`
   — удалить `metadata: ChannelMetadata`, `transports: List<TransportBinding>`, `isAutoSync`
2. Новый `domain/channel/model/ContourTransport.kt` — `data class ContourTransport(val meshtastic: MeshtasticChannel)`
3. Новый `domain/channel/model/MeshtasticChannel.kt` — `data class MeshtasticChannel(val psk: String, val channelHash: ContourHash)`
4. `domain/channel/model/LogicalChannelId.kt` → `ContourId.kt`
5. `domain/channel/model/LogicalChannelHash.kt` → `ContourHash.kt`
6. Удалить `domain/channel/model/ChannelMetadata.kt`
7. `domain/channel/repository/LogicalChannelRepository.kt` → `ContourRepository.kt`
8. `domain/channel/usecase/ObserveLogicalChannelsUseCase.kt` → `ObserveContoursUseCase.kt`
9. `domain/channel/usecase/SaveLogicalChannelUseCase.kt` → `SaveContourUseCase.kt`
10. `domain/channel/usecase/DeleteLogicalChannelUseCase.kt` → `DeleteContourUseCase.kt`
11. Новый `domain/channel/usecase/SetContourActiveUseCase.kt`

**Файлы data:**
12. `data/channel/repository/LogicalChannelRepositoryImpl.kt` → `ContourRepositoryImpl.kt`
    — mapper: `meshtastic_psk NOT NULL` → `ContourTransport(MeshtasticChannel(...))`; удалить psk=null guard
13. `data/channel/repository/FakeLogicalChannelRepository.kt` → `FakeContourRepository.kt`

**SQL:**
14. `LogicalChannel.sq` → `Contour.sq`; table `logical_channel` → `contour`;
    `meshtastic_psk TEXT NOT NULL`; добавить `is_active`, `description`, `expiration`, `exclusivity_time`;
    `is_auto_sync` — не читается (колонка остаётся в DB)
15. Создать `5.sqm`:
    ```sql
    ALTER TABLE logical_channel RENAME TO contour;
    ALTER TABLE contour ADD COLUMN is_active INTEGER NOT NULL DEFAULT 0;
    ALTER TABLE contour ADD COLUMN description TEXT;
    ALTER TABLE contour ADD COLUMN expiration INTEGER;
    ALTER TABLE contour ADD COLUMN exclusivity_time INTEGER;
    ```
    (SQLite < 3.35 не поддерживает DROP COLUMN — `is_auto_sync` и nullable `meshtastic_psk` остаются)

**Test файлы:**
16. `LogicalChannelRepositoryImplTest.kt` → `ContourRepositoryImplTest.kt`
17. `LogicalChannelHashTest.kt` → `ContourHashTest.kt`

**References update:**
18. `ChannelSlotResolverImpl.kt` — `LogicalChannelHash` → `ContourHash`
19. `MeshToChatAdapter.kt` — imports + новый routing (см. Architecture Notes)
20. `GeoMarkRepositoryImpl.kt` — imports
21. `UserSettingsViewModel.kt` — все ссылки
22. `MainViewModel.kt` — все ссылки
23. `UserSettingsModule.kt` + все DI-модули — bindings
24. `PresentationModule.kt` — bindings
25. Presentation models: `ChannelItem.kt` → `ContourItem.kt`
    (`isEmergency`, `isActive`, `description`, `expiration`, `exclusivityTime`; без `isAutoSync`, без `isDefault`; удалить `ChannelMetadata`)
26. `UserSettingsViewModelChannelsTest.kt` + `ChannelSlotResolverImplTest.kt` — imports

**String resources:**
27. Добавить `user_section_contours = "Контуры"`
28. Убрать `user_add_channel_button` (кнопка скрыта в MVP)

### Phase 2 — Emergency объект + seeding + routing

**Goal**: Emergency hardcoded; DefaultActive seeded; routing без fan-out; isActive filter работает.

1. Новый `domain/channel/model/DefaultContour.kt` — object с `asContour()`, TRANSPORT, hardcoded (см. Architecture Notes)
2. Новый `domain/channel/model/DefaultActiveContour.kt` — object с ID + DISPLAY_NAME + CHANNEL_NAME
3. `isActive` для Emergency → хранить в `DataStore` (`emergency_is_active: Boolean`, default false)
4. `ContourRepository`: добавить `seedDefaultsIfAbsent()` + `observeEmergencyIsActive(): Flow<Boolean>`
5. `ContourRepositoryImpl`: seed только DefaultActive; `observeContours()` prepend Emergency с isActive из DataStore
6. Extension `Contour.isEmergency: Boolean` в domain
7. Routing в `MeshToChatAdapter`: slot 0 → Emergency; slot N → hash-lookup; drop если !isActive
8. Routing в `IngestReceivedGeoMarksUseCase`: то же
9. `UserSettingsViewModel.computeSyncStatus()`: Emergency → `OnNode(0)`
10. `UserSettingsViewModel.onDeleteContourRequest()`: guard — `contour.isEmergency`
11. `UserSettingsViewModel`: compute `isEmergency` → передать в `ContourItem`
12. `UserSettingsViewModel.onToggleContourActive(id, isActive)`: вызов `SetContourActiveUseCase`

### Phase 3 — Sync on Connect + Geo Config

**Goal**: на connect нода получает актуальные каналы всех isActive контуров + правильный гео-режим.

**Research (перед кодингом):**
- `CommandSenderImpl.kt`: наличие `AdminMessage(set_config = ...)`, `set_channel`, `PositionConfig`,
  `position_broadcast_secs`, `ChannelSettings.position_precision`
- `GpsService.kt`: где `channel` для POSITION_APP DataPacket устанавливается
- `PositionConfig` proto: поля `gps_mode`, `position_flags`, значение `NOT_PRESENT`

**Sync on Connect:**
1. `CommandSender`: добавить `fun setChannel(slot: Int, name: String, psk: String)`
2. Новый `SyncContoursOnConnectUseCase` (см. Architecture Notes → Sync on Connect)
3. DI: добавить в `UserSettingsModule`
4. `UserSettingsViewModel`: при `Connected` → вызвать `SyncContoursOnConnectUseCase`

**App-level geo guard:**
5. Guard в position send path: отправка гео разрешена только если Emergency `isActive = false`

**Node-level geo:**
6. `CommandSender`: добавить `fun disableNodePositionBroadcast()` и `fun enableNodePositionBroadcastReady()`
7. `CommandSenderImpl`: реализовать через `sendAdmin`
8. Новые use-cases: `DisableNodePositionBroadcastUseCase`, `EnableNodePositionBroadcastReadyUseCase`
9. `UserSettingsViewModel`: при `Connected` → если Emergency isActive → disable geo, иначе → enable
10. DI: добавить оба use-cases в `UserSettingsModule`

### Phase 4 — UI

**Goal**: оба hardcoded контура защищены в UI; isActive toggle работает.

1. `ContourCard` (ранее `ChannelCard`): принять `isEmergency: Boolean`, `isActive: Boolean`
   - `isEmergency = true`: скрыть кнопку delete из dropdown
   - `isEmergency = true`: скрыть пункт "Редактировать" из dropdown
   - Показать `Switch(checked = isActive, onCheckedChange = { onToggleActive(id, it) })`
     для ВСЕХ контуров (включая emergency)
2. `UserTabContent`: кнопку "+ Добавить контур" оставить, но `enabled = false`
   ```kotlin
   // TODO(contour): разблокировать после реализации шаринга контуров (QR/import)
   ```
3. Переименовать заголовок секции: `user_section_channels` → `user_section_contours`

### Phase 5 — Tests

1. Обновить imports в переименованных тест-классах (Phase 1 tail)
2. `ContourRepositoryImplTest`:
   - DB пуста → `seedDefaultsIfAbsent()` создаёт DefaultActive (не Emergency)
   - DB уже имеет DefaultActive → повторный вызов не дублирует
   - `observeContours()` всегда prepend Emergency с isActive из DataStore
3. Routing tests (`MeshToChatAdapterTest`):
   - slot 0 + Emergency isActive=true → доставить в Emergency
   - slot 0 + Emergency isActive=false → packet dropped
   - slot N + contour isActive=true → доставить в contour
   - slot N + contour isActive=false → dropped
   - Пакет с неизвестным слотом → dropped (Log.w)
4. `SyncContoursOnConnectUseCase` tests:
   - Emergency всегда пишется на slot 0
   - isActive контуры пишутся на свободные слоты
   - Нет свободных слотов → Log.w, без crash
5. Geo config tests:
   - App-level: geo НЕ отправляется когда Emergency isActive=true
   - Node-level: `Connected` + Emergency isActive=false → `enableNodePositionBroadcastReady()`
   - Node-level: `Connected` + Emergency isActive=true → `disableNodePositionBroadcast()`
6. `SetContourActiveUseCase` tests: toggle isActive, observe изменения

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
// TODO(contour): replace hardcoded DefaultActiveContour seed with contour sharing (QR/import)
// TODO(contour): unblock Custom Контур creation after sharing is implemented
// TODO(contour): SOS mode — activate Primary automatically on alarm trigger
// TODO(contour): unblock ContourEditorSheet for Primary when SOS config UI is designed
// TODO(contour): geo mode when both slot-0 contours are active — currently DefaultActive wins
```

---

## Change Log

- 2026-04-24: создан v1 — rename sweep + Primary Контур + geo protection
- 2026-04-25: v2 — добавлен `isActive`; DefaultActiveContour ("Основной", isActive=true);
  slot-0 fan-out routing; geo config on connect зависит от isActive состояния;
  оба контура seeded при первом запуске; UI toggle для isActive
- 2026-04-25: v3 — DTO-ревью: добавлены `description`, `expiration`, `exclusivityTime`;
  удалён `isAutoSync` (sync = isActive); `isHardcoded` → `isEmergency` (extension, не хранится);
  удалён `isSystemContour` (нет разницы с `isEmergency`); `ChannelMetadata` обёртка удалена
- 2026-04-25: v4 — транспортная модель: `ContourTransport(meshtastic: MeshtasticChannel)`;
  Emergency hardcoded вне DB (isActive в DataStore); DefaultActive — обычный deletable DB-контур;
  routing без fan-out (slot 0 → Emergency, slot N → hash-lookup); Sync on Connect добавлен;
  имена: Emergency="Emergency" ch="", DefaultActive="Default contour" ch="default"
