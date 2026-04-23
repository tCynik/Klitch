# Logical Channels Management

**Date**: 2026-04-23
**Status**: Done (Phase 3 implemented, Phase 4 tests pending)

## Summary

User defines `LogicalChannel` (name + PSK) in settings. App finds the matching slot on
the connected radio automatically — no manual slot selection. Slot 0 (PRIMARY) is never
written or deleted.

## Key behaviors

- `isAutoSync` checkbox per channel — on connect, channels with `isAutoSync=true` that
  are not yet on the node are written to the first free slot (1–7)
- Manual "Записать в ноду" always available from channel dropdown
- "Удалить из ноды" — only shown when `syncStatus is OnNode && slot != 0`
- HUD info slot shows "Настройте канал" (red) when: Connected + user has ≥1 channel +
  none are on the node
- `ChannelSyncStatus` badge on each card: `OnNode(slot)`, `NotOnNode`, `NoFreeSlot`, or
  nothing when disconnected

## Architecture

### Domain models

| File | Purpose |
|---|---|
| `domain/channel/model/MeshtasticBinding.kt` | `psk: ByteArray`, `resolvedSlot: Int?`; equals/hashCode on PSK only |
| `domain/channel/model/LogicalChannel.kt` | +`isAutoSync: Boolean` |
| `domain/channel/model/NodeChannelSlot.kt` | One slot on the physical radio (index, name, psk, isEnabled) |
| `domain/channel/model/ChannelSyncStatus.kt` | `NotConnected / OnNode(slot) / NotOnNode / NoFreeSlot` |
| `domain/channel/usecase/ResolveChannelSlotUseCase.kt` | Pure logic: AlreadySynced / FreeSlot / NoFreeSlot |
| `domain/channel/usecase/ObserveNodeChannelsUseCase.kt` | Wraps `MeshConfigRepository.observeNodeChannels()` |

### Data layer

- `MeshConfigRepository.observeNodeChannels()` maps `commandSender.channelSetFlow` →
  `List<NodeChannelSlot>`; `isEnabled = index == 0 || psk.size > 0`
- DB migration `3.sqm`: `ALTER TABLE logical_channel ADD COLUMN is_auto_sync INTEGER NOT NULL DEFAULT 0`
- `LogicalChannelRepositoryImpl`: maps `resolvedSlot` (nullable), `isAutoSync`

### Presentation

- `ChannelItem`: `isAutoSync: Boolean`, `syncStatus: ChannelSyncStatus` (replaces `transportLabel`)
- `NodeWriteEvent`: `Sent(channelName) / NotConnected / NoFreeSlot`
- `UserSettingsViewModel`: single `combine(channels, nodeChannels, connectionStatus)` observer;
  auto-sync fires on connect; `onPushToNode / onDeleteFromNode / onToggleAutoSync`
- `MainViewModel`: combine observer computing `hasChannelOnNode`; HUD branch added to
  `buildConnectionInfoSlot`

### Routing impact (`resolvedSlot` replaces `channelIndex`)

Chat send, chat contact key resolution, geo-mark channel resolution, and node provisioning
all use `binding.resolvedSlot` — null means channel not yet synced → operation skipped.

## TODO (deferred)

```
// TODO(channels): write confirmation dialog before pushing to node
// TODO(channels): loading + result indication requires ACK tracking in CommandSender
// TODO(channels): "manage node slots" sheet for NoFreeSlot case
// TODO(channels): user-to-user channel import/export (QR / deep link)
```

## Tests pending (Phase 4)

- `ResolveChannelSlotUseCase`: AlreadySynced / FreeSlot / NoFreeSlot / slot-0-never-free
- `MainViewModel` HUD warning: connected+no channels / connected+channels+none on node /
  connected+match / disconnected
- `UserSettingsViewModel`: push/delete/toggle + not-connected / no-free-slot guards
