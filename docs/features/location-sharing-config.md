# Location Sharing Config

## What it does
`LocationConfigCard` in `ConfigTab` (MeshTest screen) exposes all settings that affect whether the phone's GPS reaches the mesh network — with a readiness indicator showing green/yellow/red based on blocking conditions.

## Key classes
- `LocationConfigUi` — presentation model with derived `sharingStatus: LocationSharingStatus`; `state/models/`
- `LocationSharingStatus` — sealed class: `Ready` / `Warning(reasons)` / `Blocked(reasons)` with `BlockReason` enum
- `ObserveLocationConfigUseCase` — `combine(UiPrefs.shouldProvide, localConfigFlow.position, channelFlow[0])` → `LocationConfigUi`
- `WritePositionConfigUseCase`, `WriteChannelPositionPrecisionUseCase`, `RemoveFixedPositionUseCase` — domain write operations
- `LocationConfigCard` — composable in `ConfigTab`; sections: Phone→Node, Node Source, Broadcast, Flags, Channel Precision

## Non-obvious decisions
- **Final gate**: `position_precision=0` on the primary channel means position is **never broadcast** to neighbours, regardless of all other settings. This is the most common "invisible" blocker.
- `fixed_position=true` in firmware overrides ALL incoming `sendPosition` packets — must call `RemoveFixedPositionUseCase` before enabling live GPS sharing.
- `gps_mode=ENABLED` on nodes with internal GPS (T-Beam) — no longer treated as a conflict requiring `DISABLED`. It's a fully supported parallel mode (`PositionSourceMode.NODE_GPS`, see `docs/plans/node-gps-position-source.md`): the phone bridge is explicitly not started, and `BackgroundPositionSession` writes its own cadence preset to the node (`PositionTrackingPolicy.STATIONARY_INTERVAL_SECS`). `GPS_MODE_CONFLICT` warning was removed (`node-provisioning-autoconfig.md`).
- **`position_broadcast_secs = Int.MAX_VALUE` is the normal state** when GPS sharing is enabled. The app (not the firmware) is the broadcaster. `SyncContoursOnConnectUseCase` writes `Int.MAX_VALUE` on every connect via `prepareNodeForAppDrivenBroadcast()`. The firmware never sends position autonomously in this mode. See `gps-position-staleness.md` for the full rationale.
- **Recommended firmware flags**: flags=897 (HEADING|SPEED|ALTITUDE|TIMESTAMP), precision=32. `broadcast_secs` and `smart` settings in the ConfigTab UI are irrelevant for normal operation — the app ignores them and overwrites with `Int.MAX_VALUE` on connect.
- `sharingStatus` is a pure derived property in `LocationConfigUi` — no extra state, no extra network call.

## Known limitations / planned extensions
- Settings live in `ConfigTab` (MeshTest debug screen), not in a proper NodeSettings screen — will migrate when NodeSettings is built

## Source
Plan: `docs/archive/fix-gps-sending-logic.md`
