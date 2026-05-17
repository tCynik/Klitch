# Phone GPS to Radio

## What it does
Sends the phone's GPS coordinates to the connected Meshtastic radio node so other nodes on the mesh see the correct map position.

## Key classes
- `MeshConnectionManagerImpl` — gates the pipeline via `UiPrefs.shouldProvideNodeLocation(nodeNum)`; calls `commandSender.sendPosition()` on each GPS update after clearing fixed position
- `AndroidMeshLocationManager` — subscribes to `LocationRepository.getLocations()`, calls `sendPositionFn` on each update; mesh module
- `UiPrefsImpl` — `shouldProvideNodeLocation` defaults to `true`
- `NodeSettingsViewModel` — exposes `onProvideLocationToggled(enabled)` for the UI toggle

## Non-obvious decisions
- **`setFixedPosition` first, then `sendPosition`**: for nodes with an internal GPS chip, `sendPosition` (POSITION_APP) is ignored if `fixed_position=true` in firmware config. `remove_fixed_position` admin command is sent once when sharing starts, then all updates use `sendPosition`.
- **`sendPosition` (POSITION_APP), NOT `setFixedPosition` for updates**: `setFixedPosition` is a config admin command — sets a static position and disables firmware GPS. For live tracking `sendPosition` is the correct API; firmware broadcasts it per `position_broadcast_secs`.
- `position_broadcast_secs` defaults to 900 s in firmware — position appears "frozen" until user configures it lower (30 s recommended). See LocationConfig settings.
- Pipeline gate is in `UiPrefsImpl` (`it[key] ?: true`), not in `AndroidMeshLocationManager`.

## Known limitations / planned extensions
- GPS-equipped nodes (T-Beam): firmware may re-overwrite phone coordinates with on-board GPS if `gps_mode=ENABLED` — user must set `gps_mode=DISABLED` via LocationConfigCard
- All location sharing settings (broadcast interval, flags, precision) are in the ConfigTab `LocationConfigCard` (see `fix-gps-sending-logic.md` doc)

## Source
Plan: `.claude/archive/phone-gps-to-radio.md`
