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
- **Recommended defaults**: broadcast_secs=30, smart=true, dist=25m, flags=897 (HEADING|SPEED|ALTITUDE|TIMESTAMP), precision=32
- `sharingStatus` is a pure derived property in `LocationConfigUi` — no extra state, no extra network call.

## Known limitations / planned extensions
- Settings live in `ConfigTab` (MeshTest debug screen), not in a proper NodeSettings screen — will migrate when NodeSettings is built

## Source
Plan: `.claude/archive/fix-gps-sending-logic.md`
