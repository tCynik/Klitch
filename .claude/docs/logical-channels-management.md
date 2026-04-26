# Contour Management

**Date**: 2026-04-26
**Status**: Done (renamed LogicalChannel → Contour 2026-04-25; isActive + geo protection 2026-04-26)

## Summary

User defines **Contours** (name + PSK) in settings. Each Contour has an `isActive` flag that
controls participation in send/receive. Two special system contours exist:

- **Emergency** (`DefaultContour`) — hardcoded, not in DB. `isActive = false` by default.
  Slot 0 (standard Meshtastic primary). Cannot be deleted or edited. `isActive` stored in
  DataStore (not DB). When active: geo send/receive blocked at app and node level.
- **Default contour** (`DefaultActiveContour`) — seeded into DB on first run. `isActive = true`
  by default. Deletable. Regular DB row.

App finds the matching slot on the connected radio automatically — no manual slot selection.
Slot identity is based on `channelHash = SHA-256(name.lower() + ":" + psk)[0..7]` — stable
across reconnects and slot reassignments.

## isActive semantics

| isActive | Incoming msgs | Outgoing msgs | Geo (recv) | Geo (send) |
|---|---|---|---|---|
| `true` | delivered to UI | sent | processed | sent |
| `false` | dropped | blocked | dropped | blocked |

Filtering at app level before/after network layer. Node is unaware of `isActive`.

## Key behaviors

- **Sync on connect**: on `Connected`, app writes Emergency to slot 0 + all `isActive`
  non-emergency contours to free slots (1–7); `Log.w` if no slots available
- **Manual "Записать в ноду"**: always available from contour dropdown
- **"Удалить из ноды"**: shown only when `syncStatus is OnNode && slot != 0`
- **isActive toggle**: per-contour switch; for Emergency → writes DataStore pref; for regular
  contours → updates DB row via `saveContour`
- **Emergency UI guard**: delete and edit hidden for emergency contour (`isEmergency = true`)
- **"+ Добавить контур"**: disabled in MVP (enabled after QR/import sharing is implemented)
- Incoming packets on unknown slots → dropped with `Log.w`, no UI error

## Geo protection

Two-layer protection when Emergency is active:

**App-level** (`GeoSendPolicyImpl`):
- `observeEmergencyIsActive().map { !it }` → `GeoSendPolicy.observeAllowed()`
- POSITION_APP DataPackets blocked by the consumer checking `observeAllowed()`

**Node-level** (triggered on connect):
- Emergency `isActive = false` → `enableNodePositionBroadcastReady()` (sets
  `position_broadcast_secs = 60`, `position_precision = 13` on channel 0)
- Emergency `isActive = true` → `disableNodePositionBroadcast()` (sets
  `position_broadcast_secs = UInt.MAX_VALUE`)

## Architecture

### Domain models

| File | Purpose |
|---|---|
| `domain/channel/model/ContourHash.kt` | `@JvmInline value class` wrapping 8-hex-char SHA-256 prefix; `compute(name, psk)` factory (ByteArray and String overloads) |
| `domain/channel/model/ContourId.kt` | `@JvmInline value class` wrapping String UUID |
| `domain/channel/model/Contour.kt` | `id, name, description?, expiration?, exclusivityTime?, isActive, transport: ContourTransport` + `val Contour.isEmergency` extension |
| `domain/channel/model/ContourTransport.kt` | `meshtastic: MeshtasticChannel` (future: satellite transport) |
| `domain/channel/model/MeshtasticChannel.kt` | `psk: String (base64), channelHash: ContourHash` |
| `domain/channel/model/DefaultContour.kt` | Hardcoded Emergency singleton; `asContour()`, `TRANSPORT`, `CHANNEL_HASH` |
| `domain/channel/model/DefaultActiveContour.kt` | Seed constants for DB row (ID, DISPLAY_NAME, CHANNEL_NAME) |
| `domain/channel/model/ChannelSlotMaps.kt` | `slotToHash: Map<Int, ContourHash>`, `hashToSlot: Map<ContourHash, Int>` |
| `domain/channel/model/NodeChannelSlot.kt` | One slot on the physical radio (index, name, psk, isEnabled) |
| `domain/channel/model/ChannelSyncStatus.kt` | `NotConnected / OnNode(slot) / NotOnNode / NoFreeSlot` |
| `domain/channel/ChannelSlotResolver.kt` | Interface: `slotToHash`, `hashToSlot`, `mapsFlow` |
| `domain/channel/usecase/ResolveChannelSlotUseCase.kt` | Pure sync logic: `AlreadySynced / FreeSlot / NoFreeSlot` |
| `domain/channel/usecase/SetContourActiveUseCase.kt` | Emergency ID → `setEmergencyActive()`; regular → `saveContour(copy(isActive=...))` |
| `domain/channel/usecase/SyncContoursOnConnectUseCase.kt` | Writes Emergency to slot 0 + active non-emergency to free slots |
| `domain/channel/usecase/ObserveContoursUseCase.kt` | Wraps `ContourRepository.observeContours()` |
| `domain/channel/usecase/ObserveNodeChannelsUseCase.kt` | Wraps `MeshConfigRepository.observeNodeChannels()` |
| `domain/channel/usecase/SaveContourUseCase.kt` | Wraps `ContourRepository.saveContour()` |
| `domain/channel/usecase/DeleteContourUseCase.kt` | Wraps `ContourRepository.deleteContour()` |

### Repository interface

```kotlin
interface ContourRepository {
    fun observeContours(): Flow<List<Contour>>      // Emergency always prepended
    fun observeEmergencyIsActive(): Flow<Boolean>   // reads DataStore pref
    suspend fun setEmergencyActive(isActive: Boolean)
    suspend fun seedDefaultsIfAbsent()              // seeds DefaultActiveContour on first run
    suspend fun saveContour(contour: Contour)
    suspend fun deleteContour(id: ContourId)
    suspend fun findByChannelHash(hash: ContourHash): Contour?
}
```

### Data layer

- `ContourRepositoryImpl`:
  - `observeContours()` = `combine(DB flow, DataStore pref)` → prepend Emergency with live `isActive`
  - `seedDefaultsIfAbsent()` = upsert DefaultActiveContour row if absent (called on app start)
  - Emergency `isActive` in `DataStore<Preferences>` key `emergency_is_active`
  - DB mapper: `meshtastic_psk NOT NULL` → `ContourTransport(MeshtasticChannel(...))`; backfills
    `channel_hash IS NULL` rows on first read
- `ChannelSlotResolverImpl` — singleton, subscribes to `ObserveNodeChannelsUseCase`, maintains
  live `ChannelSlotMaps`; no DB access
- `GeoSendPolicyImpl` — `observeAllowed() = observeEmergencyIsActive().map { !it }`
- `MeshConfigRepository.observeNodeChannels()` maps `commandSender.channelSetFlow` →
  `List<NodeChannelSlot>`; `isEnabled = index == 0 || psk.size > 0`
- DB migrations:
  - `3.sqm`: ADD COLUMN `is_auto_sync` (legacy, no longer read)
  - `4.sqm`: ADD COLUMN `channel_hash TEXT`
  - `5.sqm`: RENAME TABLE `logical_channel → contour`; ADD COLUMNS `is_active`, `description`,
    `expiration`, `exclusivity_time`

### Routing (incoming packets)

```
incoming packet.channel (Int)
  slot == 0  → DefaultContour.asContour() with isActive from DataStore
  slot N     → ChannelSlotResolver.slotToHash[N]  → ContourHash (or drop + Log.w)
             → contours.find { it.transport.meshtastic.channelHash == hash }  (or drop + Log.w)
  takeIf { it.isActive }  →  deliver  (or drop if inactive)
```

Used in: `IngestReceivedGeoMarksUseCase`, `MeshToChatAdapter`.

### Sync on Connect (`SyncContoursOnConnectUseCase`)

```
on Connected:
  writeChannel(0, "", "AQ==")             // Emergency always on slot 0
  for each isActive non-emergency contour:
    AlreadySynced → skip
    FreeSlot(N)   → writeChannel(N, name, pskBase64)
    NoFreeSlot    → Log.w, return
```

### Presentation

- `ContourItem`: `id, name, description?, expiration?, exclusivityTime?, isActive, isEmergency, syncStatus`
- `NodeWriteEvent`: `Sent(channelName) / NotConnected / NoFreeSlot`
- `UserSettingsViewModel`:
  - `combine(contours, nodeChannels, connectionStatus)` observer
  - On `justConnected`: `syncContoursOnConnect()` + geo config (enable/disable)
  - `onToggleActive(id, isActive)` → `SetContourActiveUseCase`
  - `onPushToNode / onDeleteFromNode` — slot resolution via `ChannelSlotResolver`
  - Emergency guard: delete/edit blocked in ViewModel and UI

### DI registration

```kotlin
// userSettingsModule
single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }
single { SyncContoursOnConnectUseCase(get(), get(), get(), get()) }
single { EnableNodePositionBroadcastReadyUseCase(get()) }
single { DisableNodePositionBroadcastUseCase(get()) }
```

## TODO (deferred)

```kotlin
// TODO(contour): replace hardcoded DefaultContour seed with contour sharing (QR/import)
// TODO(contour): unblock Custom Контур creation after sharing is implemented
// TODO(contour): SOS mode — activate Emergency automatically on alarm trigger
// TODO(contour): unblock ContourEditorSheet for Emergency when SOS config UI is designed
// TODO(contour): geo mode when both slot-0 contours are active — currently DefaultActive wins
// TODO(contour): обработать отсутствие свободных слотов (UI уведомление)
// TODO(contour): DROP COLUMN meshtastic_slot when minSdk ≥ 35
```
