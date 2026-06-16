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
- `gps_mode=ENABLED` on nodes with internal GPS (T-Beam) causes firmware to overwrite phone coordinates with its own fix — user must set `DISABLED`.
- **`position_broadcast_secs = Int.MAX_VALUE` is the normal state** when GPS sharing is enabled. The app (not the firmware) is the broadcaster. `SyncContoursOnConnectUseCase` writes `Int.MAX_VALUE` on every connect via `prepareNodeForAppDrivenBroadcast()`. The firmware never sends position autonomously in this mode. See `gps-position-staleness.md` for the full rationale.
- **Recommended firmware flags**: flags=897 (HEADING|SPEED|ALTITUDE|TIMESTAMP), precision=32. `broadcast_secs` and `smart` settings in the ConfigTab UI are irrelevant for normal operation — the app ignores them and overwrites with `Int.MAX_VALUE` on connect.
- `sharingStatus` is a pure derived property in `LocationConfigUi` — no extra state, no extra network call.

## Known limitations / planned extensions
- Settings live in `ConfigTab` (MeshTest debug screen), not in a proper NodeSettings screen — will migrate when NodeSettings is built

## Source
Plan: `docs/archive/fix-gps-sending-logic.md`
