# Logical Channels Management

**Date**: 2026-04-23
**Status**: Done (refactored to hash-based identity 2026-04-24)

## Summary

User defines `LogicalChannel` (name + PSK) in settings. App finds the matching slot on
the connected radio automatically — no manual slot selection. Slot 0 (PRIMARY) is never
written or deleted.

Channel identity is based on `channelHash = SHA-256(name + ":" + psk)[0..7]` — stable
across node reconnects, slot reassignments, and offline usage. Slot↔hash mapping is
resolved at runtime by `ChannelSlotResolver`, not stored in the database.

## Key behaviors

- `isAutoSync` checkbox per channel — on connect, channels with `isAutoSync=true` that
  are not yet on the node are written to the first free slot (1–7)
- Manual "Записать в ноду" always available from channel dropdown
- "Удалить из ноды" — only shown when `syncStatus is OnNode && slot != 0`
- HUD info slot shows "Настройте канал" (red) when: Connected + user has ≥1 channel +
  none are on the node
- `ChannelSyncStatus` badge on each card: `OnNode(slot)`, `NotOnNode`, `NoFreeSlot`, or
  nothing when disconnected
- Incoming packets on slots absent from the resolver → dropped with `Log.w`, no UI

## Architecture

### Domain models

| File | Purpose |
|---|---|
| `domain/channel/model/LogicalChannelHash.kt` | `@JvmInline value class` wrapping 8-hex-char SHA-256 prefix; `compute(name, psk)` factory |
| `domain/channel/model/ChannelSlotMaps.kt` | `slotToHash: Map<Int, LogicalChannelHash>`, `hashToSlot: Map<LogicalChannelHash, Int>` |
| `domain/channel/model/MeshtasticBinding.kt` | `psk: ByteArray`, `channelHash: LogicalChannelHash`; equals/hashCode on PSK only |
| `domain/channel/model/LogicalChannel.kt` | `+isAutoSync: Boolean` |
| `domain/channel/model/NodeChannelSlot.kt` | One slot on the physical radio (index, name, psk, isEnabled) |
| `domain/channel/model/ChannelSyncStatus.kt` | `NotConnected / OnNode(slot) / NotOnNode / NoFreeSlot` |
| `domain/channel/ChannelSlotResolver.kt` | Interface: `slotToHash`, `hashToSlot`, `mapsFlow` — runtime slot↔hash mapping |
| `domain/channel/usecase/ResolveChannelSlotUseCase.kt` | Pure logic: AlreadySynced / FreeSlot / NoFreeSlot |
| `domain/channel/usecase/ObserveNodeChannelsUseCase.kt` | Wraps `MeshConfigRepository.observeNodeChannels()` |

### Data layer

- `ChannelSlotResolverImpl` — singleton, subscribes to `ObserveNodeChannelsUseCase` in `init`,
  maintains live `ChannelSlotMaps`; no DB access
- `MeshConfigRepository.observeNodeChannels()` maps `commandSender.channelSetFlow` →
  `List<NodeChannelSlot>`; `isEnabled = index == 0 || psk.size > 0`
- DB migration `3.sqm`: `ALTER TABLE logical_channel ADD COLUMN is_auto_sync INTEGER NOT NULL DEFAULT 0`
- DB migration `4.sqm`: `ALTER TABLE logical_channel ADD COLUMN channel_hash TEXT`
- `LogicalChannelRepositoryImpl`: computes and persists `channelHash` on write; backfills
  rows with `channel_hash IS NULL` on first read; `meshtastic_slot` column kept but no
  longer written (deprecated, DROP COLUMN deferred to minSdk ≥ 35)
- `LogicalChannelRepository.findByChannelHash(hash)` — used by `GeoMarkRepositoryImpl`

### Routing (hash-based, replaces resolvedSlot)

```
incoming packet.channel (Int)
  → ChannelSlotResolver.slotToHash[slot]   → LogicalChannelHash (or drop)
  → channelByHash[hash]                    → LogicalChannelId (UUID)
  → stored in chat_message / geo_mark
```

`channelByHash` is built in-memory inside the combine block from `observeChannels()` — no
extra DB query per packet. UUID remains DB primary key; `channelHash` is a secondary unique
index used only for runtime routing.

**Why UUID stays as PK (not channelHash):** UUID was the PK before hash existed; changing
the PK would require a full table migration cascading to `chat_message` and `geo_mark`
foreign keys. No benefit in MVP — hash is only needed for the routing lookup path.

### Presentation

- `ChannelItem`: `isAutoSync: Boolean`, `syncStatus: ChannelSyncStatus` (replaces `transportLabel`)
- `NodeWriteEvent`: `Sent(channelName) / NotConnected / NoFreeSlot`
- `UserSettingsViewModel`: single `combine(channels, nodeChannels, connectionStatus)` observer;
  auto-sync fires on connect; `onPushToNode / onDeleteFromNode / onToggleAutoSync`;
  slot resolved via `ChannelSlotResolver.hashToSlot` — no stored slot read
- `MainViewModel`: combine observer computing `hasChannelOnNode`; HUD branch added to
  `buildConnectionInfoSlot`

### DI registration

```kotlin
// userSettingsModule
single<ChannelSlotResolver> { ChannelSlotResolverImpl(get()) }

// chatDataModule — resolver injected into MeshToChatAdapter + IngestReceivedChatMessagesUseCase
// geoMarkDataModule — resolver injected into GeoMarkRepositoryImpl + IngestReceivedGeoMarksUseCase
// meshDataModule — NodeProvisioningUseCase gets ObserveNodeChannelsUseCase + ResolveChannelSlotUseCase
```

## TODO (deferred)

```
// TODO(channels): write confirmation dialog before pushing to node
// TODO(channels): loading + result indication requires ACK tracking in CommandSender
// TODO(channels): "manage node slots" sheet for NoFreeSlot case
// TODO(channels): user-to-user channel import/export (QR / deep link)
// TODO(channels): DROP COLUMN meshtastic_slot when minSdk ≥ 35
```

## Tests pending (Phase 4)

From original plan:
- `ResolveChannelSlotUseCase`: AlreadySynced / FreeSlot / NoFreeSlot / slot-0-never-free
- `MainViewModel` HUD warning: connected+no channels / connected+channels+none on node /
  connected+match / disconnected
- `UserSettingsViewModel`: push/delete/toggle + not-connected / no-free-slot guards

From identity refactor:
- `LogicalChannelHash.compute`: deterministic, stable, different outputs for different inputs
- `ChannelSlotResolverImpl`: maps built correctly; empty list → empty maps
- `IngestReceivedChatMessagesUseCase`: routed to correct channel by slot; unresolved → dropped
- `IngestReceivedGeoMarksUseCase`: same
- DB backfill: row with `channel_hash = null` → hash computed and persisted on first load
