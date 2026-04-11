# Fix: GPS Sending Logic + Location Settings in ConfigTab

**Date**: 2026-04-11
**Status**: Ready for implementation

---

## Root Cause (discovered in field test)

Two independent root causes identified:

1. **Primary:** "Provide location to mesh" toggle was turned off. No UI for this setting existed in ConfigTab → user could not see or enable it.
2. **Architectural:** `MeshConnectionManagerImpl` uses `setFixedPosition` (Admin = static config) instead of `sendPosition` (POSITION_APP = live packet). The node was only rebroadcasting position on the `position_broadcast_secs` timer (900 s default).

---

## All Factors Affecting Location Exchange

### App side (Phone → Node)

| # | Factor | Source | Default |
|---|---|---|---|
| 1 | **Provide location to mesh** — master switch, enables the GPS pipeline | `UiPrefs.shouldProvideNodeLocation(nodeNum)` DataStore | `true` (was turned off!) |
| 2 | **Location permission** — `ACCESS_FINE_LOCATION` must be granted | `context.hasLocationPermission()` in `AndroidMeshLocationManager` | — |
| 3 | **Send method** — `sendPosition` (POSITION_APP) vs `setFixedPosition` (Admin) | `MeshConnectionManagerImpl`, lines 117–135 | ❌ currently `setFixedPosition` |
| 4 | **GPS update interval** — how often the phone queries GPS | `LocationRepositoryImpl.DEFAULT_INTERVAL_MS = 30_000L` | 30 s (hardcoded) |

### Node firmware side (Node internals + Node → Mesh)

| # | Factor | Proto field | Default |
|---|---|---|---|
| 5 | **gps_mode** — position source: DISABLED / ENABLED / NOT_PRESENT | `Config.PositionConfig.gps_mode` | `DISABLED` |
| 6 | **fixed_position** — static coordinates, overrides everything | `Config.PositionConfig.fixed_position` | `false` |
| 7 | **position_broadcast_secs** — how often node broadcasts position to mesh | `Config.PositionConfig.position_broadcast_secs` | **900** (15 min!) |
| 8 | **position_broadcast_smart_enabled** — adaptive broadcast on movement | `Config.PositionConfig.position_broadcast_smart_enabled` | `false` |
| 9 | **position_flags** — which fields to include in the position packet | `Config.PositionConfig.position_flags` (bitmask) | `0` |
| 10 | **broadcast_smart_minimum_distance** — min movement to trigger smart broadcast | `Config.PositionConfig.broadcast_smart_minimum_distance` | — |
| 11 | **gps_update_interval** — how often node polls GPS chip (only when gps_mode=ENABLED) | `Config.PositionConfig.gps_update_interval` | 300 s |

### Channel side (Channel → Mesh delivery)

| # | Factor | Proto field | Default |
|---|---|---|---|
| 12 | **position_precision** — position precision when sharing on a channel; **0 = position not transmitted** | `Channel.settings.module_settings.position_precision` | 32 (primary), but may be 0! |

### Position source priority in firmware

```
fixed_position=true       → everything ignored, static coords only (HIGHEST PRIORITY)
gps_mode=ENABLED          → GPS chip overwrites phone coordinates on every fix
gps_mode=DISABLED/NOT_PRESENT + sendPosition → phone coordinates are the sole source
```

### Final gate before mesh delivery

```
position_precision=0 on all channels → position is not broadcast to neighbours (regardless of everything above)
```

> The node may have a valid internal position but will not share it to the mesh if primary channel `position_precision = 0`.

### position_flags bitmask (`Config.PositionFlags`)

| Flag | Value | Required for |
|---|---|---|
| `ALTITUDE` | 1 | Altitude (HAE) |
| `ALTITUDE_MSL` | 2 | Altitude above sea level |
| `SATINVIEW` | 32 | Satellite count |
| `SEQ_NO` | 64 | Packet sequence number |
| `TIMESTAMP` | 128 | Timestamp |
| `HEADING` | 256 | Heading — **critical for directional_nodes_marks** |
| `SPEED` | 512 | Speed — **critical for directional_nodes_marks** |

---

## Scope

**Phase 1 — Bug fix: sendPosition instead of setFixedPosition**
- `MeshConnectionManagerImpl.kt` — 1 file, ~15 lines
- Call `remove_fixed_position` once when sharing starts

**Phase 2 — Settings UI in ConfigTab**
- New card `LocationConfigCard` in `ConfigTab`
- New `data class LocationConfigUi` + `LocationSharingStatus` sealed class
- New use cases: `ObserveLocationConfigUseCase`, `SetProvideLocationUseCase`, `WritePositionConfigUseCase`, `WriteChannelPositionPrecisionUseCase`, `RemoveFixedPositionUseCase`
- Extended `MeshTestViewModel` with new actions

---

## Phase 1 — sendPosition instead of setFixedPosition

### File
[MeshConnectionManagerImpl.kt](mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/data/manager/MeshConnectionManagerImpl.kt)

### Change (lines 117–135)

```kotlin
// BEFORE:
if (shouldProvide) {
    locationManager.start(scope) { pos ->
        commandSender.setFixedPosition(nodeNum, Position(lat, lon, alt))
    }
} else {
    locationManager.stop()
    commandSender.setFixedPosition(nodeNum, Position(0.0, 0.0, 0))
}

// AFTER:
if (shouldProvide) {
    // Clear fixed_position — otherwise firmware ignores sendPosition
    commandSender.setFixedPosition(myNodeEntity.myNodeNum, Position(0.0, 0.0, 0))
    locationManager.start(scope) { pos ->
        Logger.i { "PhoneGPS→radio: sendPosition lat=... lon=..." }
        commandSender.sendPosition(pos)
    }
} else {
    locationManager.stop()
    // Do not touch fixed_position — node will use its own GPS if available
}
```

---

## Phase 2 — Settings UI in ConfigTab

### Cards to add

Add a new card **LocationConfigCard** in `ConfigTab` after `DeviceConfigCard`.

#### Position Readiness Indicator (top of card)

A summary banner computed from `LocationConfigUi`. Shows:

- **Green / "Ready"** — all conditions for live GPS sharing are satisfied
- **Yellow / "Warning"** — sharing works but suboptimal (e.g. broadcast interval too high, flags missing)
- **Red / "Blocked"** — at least one hard blocker is active

**Blocking conditions:**

| Condition | Severity | Message |
|---|---|---|
| `provideLocationToMesh = false` | Red | Location sharing is disabled |
| `hasLocationPermission = false` | Red | Location permission not granted |
| `fixedPositionEnabled = true` | Red | Fixed position is active — overrides live GPS |
| `primaryChannelPositionPrecision = 0` | Red | Position not transmitted on primary channel |
| `positionFlags = 0` | Yellow | No position fields selected (empty packet) |
| `broadcastIntervalSecs > 120` | Yellow | Broadcast interval is high (>{value}s) |
| `gpsMode = ENABLED` (when phone GPS is used) | Yellow | Node GPS chip may overwrite phone coordinates |

The indicator is a pure derived property — no new state, no new network call. Computed inside `LocationConfigUi` or as an extension function.

#### Section A: Phone → Node

| Setting | UI | Write source | Read source |
|---|---|---|---|
| Provide location to mesh | `Switch` | `UiPrefs.setShouldProvideNodeLocation(nodeNum, v)` | `UiPrefs.shouldProvideNodeLocation(nodeNum)` |
| Location permission status | `Text` (read-only, colour indicator) | — | `context.hasLocationPermission()` |

#### Section B: Node Position Source

| Setting | UI | Proto field | Write |
|---|---|---|---|
| GPS mode | `Dropdown` (DISABLED / ENABLED / NOT_PRESENT) | `gps_mode` | `setConfig(Config(position = cfg.copy(gps_mode = v)))` |
| Fixed position | `Text` (status) + "Remove" button | `fixed_position` | `commandSender.setFixedPosition(nodeNum, Position(0,0,0))` |

#### Section C: Node → Mesh Broadcast

| Setting | UI | Proto field | Write |
|---|---|---|---|
| Broadcast interval (s) | `OutlinedTextField` (number) | `position_broadcast_secs` | `setConfig(Config(position = cfg.copy(...)))` |
| Smart broadcast | `Switch` | `position_broadcast_smart_enabled` | `setConfig(...)` |
| Min smart distance (m) | `OutlinedTextField` | `broadcast_smart_minimum_distance` | `setConfig(...)` |

#### Section D: Position Payload Flags

| Setting | UI | Bitmask value |
|---|---|---|
| Include altitude | `Checkbox` | `PositionFlags.ALTITUDE` (1) |
| Include MSL altitude | `Checkbox` | `PositionFlags.ALTITUDE_MSL` (2) |
| Include heading | `Checkbox` ⭐ | `PositionFlags.HEADING` (256) |
| Include speed | `Checkbox` ⭐ | `PositionFlags.SPEED` (512) |
| Include timestamp | `Checkbox` | `PositionFlags.TIMESTAMP` (128) |
| Include sats in view | `Checkbox` | `PositionFlags.SATINVIEW` (32) |

> ⭐ HEADING + SPEED are critical for `directional_nodes_marks`

#### Section E: Channel Position Precision

| Setting | UI | Proto field | Write |
|---|---|---|---|
| Position precision (primary channel) | `Dropdown` (0=Off / 10=~11km / 13=~1.4km / 16=~170m / 19=~21m / 32=Full) | `Channel.settings.module_settings.position_precision` | `radioController.setChannel(channel.copy(...))` |

> `position_precision = 0` — position is not transmitted on this channel, regardless of all other settings. **Final gate in the chain.**

---

### New data models

**File**: `state/models/LocationConfigUi.kt`

```kotlin
data class LocationConfigUi(
    // Phone → Node
    val provideLocationToMesh: Boolean,
    val hasLocationPermission: Boolean,
    // Node Position Source
    val gpsMode: GpsModeUi,                       // DISABLED / ENABLED / NOT_PRESENT
    val fixedPositionEnabled: Boolean,
    val fixedPositionLat: Double?,
    val fixedPositionLon: Double?,
    // Node → Mesh Broadcast
    val broadcastIntervalSecs: Int,               // UI default: 30
    val smartBroadcastEnabled: Boolean,           // UI default: true
    val smartBroadcastMinDistanceM: Int,          // UI default: 25
    // Payload Flags
    val positionFlags: Int,                       // bitmask; UI default: HEADING|SPEED|ALTITUDE|TIMESTAMP = 897
    // Channel Position Precision
    val primaryChannelPositionPrecision: Int,     // 0 = off, 32 = full; final gate
) {
    val sharingStatus: LocationSharingStatus get() = computeSharingStatus()

    private fun computeSharingStatus(): LocationSharingStatus {
        val blockers = mutableListOf<BlockReason>()
        val warnings = mutableListOf<BlockReason>()

        if (!provideLocationToMesh)              blockers += BlockReason.PROVIDE_LOCATION_DISABLED
        if (!hasLocationPermission)              blockers += BlockReason.LOCATION_PERMISSION_DENIED
        if (fixedPositionEnabled)                blockers += BlockReason.FIXED_POSITION_ACTIVE
        if (primaryChannelPositionPrecision == 0) blockers += BlockReason.CHANNEL_PRECISION_DISABLED

        if (positionFlags == 0)                  warnings += BlockReason.NO_POSITION_FLAGS
        if (broadcastIntervalSecs > 120)         warnings += BlockReason.BROADCAST_INTERVAL_HIGH
        if (gpsMode == GpsModeUi.ENABLED)        warnings += BlockReason.GPS_MODE_CONFLICT

        return when {
            blockers.isNotEmpty() -> LocationSharingStatus.Blocked(blockers)
            warnings.isNotEmpty() -> LocationSharingStatus.Warning(warnings)
            else -> LocationSharingStatus.Ready
        }
    }
}

enum class GpsModeUi { DISABLED, ENABLED, NOT_PRESENT }

enum class BlockReason {
    PROVIDE_LOCATION_DISABLED,
    LOCATION_PERMISSION_DENIED,
    FIXED_POSITION_ACTIVE,
    CHANNEL_PRECISION_DISABLED,
    NO_POSITION_FLAGS,
    BROADCAST_INTERVAL_HIGH,
    GPS_MODE_CONFLICT,
}

sealed class LocationSharingStatus {
    object Ready : LocationSharingStatus()
    data class Warning(val reasons: List<BlockReason>) : LocationSharingStatus()
    data class Blocked(val reasons: List<BlockReason>) : LocationSharingStatus()
}
```

**Extend** `ConfigTabState`:

```kotlin
data class ConfigTabState(
    // ... existing fields ...
    val locationConfig: LocationConfigUi? = null,
)
```

---

### New use cases

| Use Case | Parameters | Action |
|---|---|---|
| `ObserveLocationConfigUseCase` | `nodeNum: Int` | `combine(UiPrefs.shouldProvide, localConfigFlow.position, channelFlow[0])` → `LocationConfigUi` |
| `SetProvideLocationUseCase` | `nodeNum: Int, value: Boolean` | `uiPrefs.setShouldProvideNodeLocation(nodeNum, value)` |
| `WritePositionConfigUseCase` | `destNum: Int, config: PositionConfig` | `radioController.setConfig(destNum, Config(position = config), packetId)` |
| `WriteChannelPositionPrecisionUseCase` | `destNum: Int, channelIndex: Int, precision: Int` | `radioController.setChannel(destNum, channel.copy(position_precision = precision), packetId)` |
| `RemoveFixedPositionUseCase` | `destNum: Int` | `radioController.setFixedPosition(destNum, Position(0,0,0))` |

---

### MeshTestViewModel extensions

Add to constructor:
- `ObserveLocationConfigUseCase`
- `SetProvideLocationUseCase`
- `WritePositionConfigUseCase`
- `WriteChannelPositionPrecisionUseCase`
- `RemoveFixedPositionUseCase`

Add handlers:
```kotlin
fun onProvideLocationToggle(enabled: Boolean)
fun onGpsModeChange(mode: GpsModeUi)
fun onRemoveFixedPosition()
fun onBroadcastIntervalChange(secs: Int)
fun onSmartBroadcastToggle(enabled: Boolean)
fun onPositionFlagsChange(flags: Int)
fun onChannelPositionPrecisionChange(precision: Int)
```

---

### ConfigTab and MeshTestScreen extensions

`ConfigTab` gains additional parameters:
```kotlin
locationConfig: LocationConfigUi?,
onProvideLocationToggle: (Boolean) -> Unit,
onGpsModeChange: (GpsModeUi) -> Unit,
onRemoveFixedPosition: () -> Unit,
onBroadcastIntervalChange: (Int) -> Unit,
onSmartBroadcastToggle: (Boolean) -> Unit,
onPositionFlagsChange: (Int) -> Unit,
onChannelPositionPrecisionChange: (Int) -> Unit,
```

---

## Recommended values for live phone GPS

```
provide_location_to_mesh             = true           ← master switch
gps_mode                             = DISABLED       ← do not compete with GPS chip
fixed_position                       = false          ← do not block phone GPS
position_broadcast_secs              = 30             ← instead of 900 (practical minimum)
position_broadcast_smart_enabled     = true           ← adaptive broadcast
broadcast_smart_minimum_distance     = 25             ← 25 metres
position_flags                       = HEADING(256) | SPEED(512) | ALTITUDE(1) | TIMESTAMP(128) = 897
primary_channel.position_precision   = 32             ← full precision (verify it is not 0!)
```

---

## Activation sequence

```
User: ConfigTab → "Provide location to mesh" → ON
  → SetProvideLocationUseCase(nodeNum, true) → DataStore
  → RemoveFixedPositionUseCase(nodeNum)      → Admin remove_fixed_position
  → MeshConnectionManagerImpl sees shouldProvide=true
      → commandSender.sendPosition(pos)      → POSITION_APP every 30 s
          → LOC_EXTERNAL tag in packet
          → Firmware updates node position DB
          → Node broadcasts per position_broadcast_secs (30 s → every 30 s)

Other nodes:
  → Receive POSITION_APP
  → NodeManager.handleReceivedPosition()
  → GeoNodesTab updates
  → Map updates
```

---

## Implementation order

```
1. Phase 1:  MeshConnectionManagerImpl — bug fix (independent of UI)
2. Phase 2a: Data models — LocationConfigUi, GpsModeUi, BlockReason, LocationSharingStatus (new files)
3. Phase 2b: Use cases — ObserveLocationConfig, SetProvideLocation, WritePositionConfig,
             WriteChannelPositionPrecision, RemoveFixedPosition
4. Phase 2c: MeshTestViewModel — add use cases + handlers
5. Phase 2d: ConfigTabState — add locationConfig field
6. Phase 2e: LocationConfigCard composable — new file (includes readiness indicator)
7. Phase 2f: ConfigTab — add LocationConfigCard, new parameters
8. Phase 2g: MeshTestScreen — wire new handlers
```

---

## Risks

| Risk | Probability | Mitigation |
|---|---|---|
| GPS-equipped node (T-Beam) overwrites LOC_EXTERNAL with its own GPS fix | Medium | gps_mode selector in UI — user explicitly sets DISABLED |
| `remove_fixed_position` rejected due to passkey mismatch | Low | Passkey refreshes on next successful Admin exchange |
| `WritePositionConfigUseCase` not yet in domain | — | Add in Phase 2b |
| `setChannel` API may require fetching full channel list first | Low | Read `localChannelFlow` in use case before modifying |

---

## Change Log

- 2026-04-11: created
- 2026-04-11: updated — added real root cause (toggle was off)
- 2026-04-11: reworked — ConfigTab instead of NodeSettings, full analysis of all flow factors
- 2026-04-11: added factor #12 — channel position_precision (final gate); Section E in ConfigTab; WriteChannelPositionPrecisionUseCase; corrected UI defaults (broadcast_secs=30, smart=true, dist=25, flags=897, precision=32)
- 2026-04-11: rewrote to English; added Position Readiness Indicator with LocationSharingStatus sealed class and BlockReason enum (pure derived property, no extra state)
