# GPS Setup on Meshtastic Node — Research Summary

## Overview

This document summarizes how GPS/location configuration works in the Meshtastic Android application, including the data flow from UI to device, key configuration fields, and steps to enable GPS on a connected node.

---

## How to Enable GPS on a Node

1. **Connect to the node** via BLE (Bluetooth Low Energy)
2. **Navigate to:** Node → Config → **Position** (Position Config screen)
3. **Set GPS Mode:** Change `gps_mode` from `DISABLED` or `NOT_PRESENT` to **`ENABLED`**
4. **Save** — the app automatically sends the configuration to the device

> **Note:** If the node does not have a physical GPS module, enabling `gps_mode = ENABLED` will not work. In that case, use **Fixed Position** to set coordinates manually.

---

## Key Configuration Fields

| Field | Type | Description | Default |
|-------|------|-------------|---------|
| `gps_mode` | `GpsMode` enum | GPS state: `DISABLED` (0), `ENABLED` (1), `NOT_PRESENT` (2) | `DISABLED` |
| `position_broadcast_secs` | `uint32` | How often to broadcast position (seconds) | `900` (15 min) |
| `position_broadcast_smart_enabled` | `bool` | Adaptive broadcast based on movement | `true` |
| `gps_update_interval` | `uint32` | How often to query GPS hardware (seconds) | `300` (5 min) |
| `fixed_position` | `bool` | Use manual coordinates instead of GPS | `false` |
| `position_flags` | `uint32` | Bitmask: what data to include (altitude, speed, heading, etc.) | — |
| `gps_en_gpio` | `uint32` | GPIO pin to enable GPS hardware (if applicable) | — |

**`GpsMode` enum:**
- `DISABLED = 0` — GPS hardware present but turned off
- `ENABLED = 1` — GPS hardware present and active
- `NOT_PRESENT = 2` — No GPS hardware on this device

---

## Architecture & Data Flow

```
┌─────────────────────┐
│  PositionConfigUI   │  ← feature/settings/.../PositionConfigItemList.kt
│  (Jetpack Compose)  │
└─────────┬───────────┘
          │ user changes gps_mode
          ▼
┌─────────────────────┐
│ RadioConfigViewModel│  ← setConfig(config: Config)
└─────────┬───────────┘
          │ radioConfigUseCase.setConfig(destNum, config)
          ▼
┌─────────────────────┐
│  RadioConfigUseCase │  ← gets packetId, delegates to RadioController
└─────────┬───────────┘
          │ radioController.setConfig(destNum, config, packetId)
          ▼
┌─────────────────────┐
│ AndroidRadioController│ ← meshService.setRemoteConfig(packetId, destNum, config.encode())
└─────────┬───────────┘
          │ AIDL / Service
          ▼
┌─────────────────────┐
│   Meshtastic Device │  ← receives Config with PositionConfig.gps_mode = ENABLED
└─────────────────────┘
```

---

## Key Files

| File | Purpose |
|------|---------|
| `core/proto/.../config.proto` | Proto definition: `PositionConfig`, `GpsMode` enum, `PositionFlags` |
| `core/proto/.../admin.proto` | `ConfigType.POSITION_CONFIG = 1`, admin message for setting config |
| `feature/settings/.../PositionConfigItemList.kt` | Composable UI for GPS/position settings |
| `feature/settings/.../RadioConfigViewModel.kt` | ViewModel: `setConfig()`, `setFixedPosition()` |
| `core/domain/.../RadioConfigUseCase.kt` | Use case: orchestrates config save |
| `core/service/.../AndroidRadioControllerImpl.kt` | Sends config to device via MeshService |
| `core/datastore/.../LocalConfigDataSource.kt` | Saves position config locally in DataStore |

---

## Additional: Fixed Position (No GPS Hardware)

If the node lacks a GPS module, you can set fixed coordinates:

```kotlin
// In RadioConfigViewModel
fun setFixedPosition(position: Position) {
    val destNum = destNode.value?.num ?: return
    viewModelScope.launch { radioConfigUseCase.setFixedPosition(destNum, position) }
}

fun removeFixedPosition() {
    val destNum = destNode.value?.num ?: return
    viewModelScope.launch { radioConfigUseCase.removeFixedPosition(destNum) }
}
```

This sends a `Position` object with latitude, longitude, and altitude to the device, and sets `fixed_position = true`.

---

## Phone Location Sharing — Sending Phone GPS to Node (No GPS Hardware Required)

When the node **has no GPS module**, you can still broadcast your phone's location through the mesh. This is a **separate feature** from Fixed Position or enabling GPS on the node.

### How to Enable

1. Open the app and connect to your node via BLE
2. Go to **Settings** → **Privacy** section
3. Toggle **"Provide Location to Mesh"** (`provide_location_to_mesh`)
4. Grant **Location Permission** when prompted
5. Ensure **GPS is enabled** on your phone

Once enabled, the app will automatically send your phone's GPS coordinates to the connected node every **~30 seconds**, and the node will relay them into the mesh.

### How It Works

```
Phone GPS → LocationManager (30s interval) → ProtoPosition(LOC_EXTERNAL)
    → CommandSender.sendPosition() → POSITION_APP packet → BLE → Node → Mesh
```

**Key details:**
- **Location source:** `LOC_EXTERNAL` — marks the position as coming from an external GPS (the phone)
- **Packet type:** Decoded `Position` via `POSITION_APP` (not an admin command)
- **Update interval:** ~30 seconds (`DEFAULT_INTERVAL_MS = 30_000`)
- **Auto-start:** If previously enabled, location sharing resumes automatically on each connection
- **Foreground service:** The `MeshService` runs as a foreground service, so it continues sending location even when the app is in the background

### Permissions Required

| Permission | Purpose |
|-----------|---------|
| `ACCESS_FINE_LOCATION` | Access phone GPS coordinates |
| GPS enabled on phone | System location services must be on |

> **Note:** No `ACCESS_BACKGROUND_LOCATION` permission is needed because `MeshService` runs as a foreground service with a persistent notification.

### Difference: Phone Location vs Fixed Position

| Feature | Phone Location Sharing | Fixed Position |
|---------|----------------------|----------------|
| **Source** | Live phone GPS (dynamic) | Manual static coordinates |
| **Mechanism** | `POSITION_APP` packet every ~30s | `AdminMessage.set_fixed_position` |
| **Requires phone** | Yes (connected & running) | No (stored on node) |
| **Use case** | You're moving with the node | Node is at a known fixed location |
| **LocationSource** | `LOC_EXTERNAL` | N/A (stored on device) |

### Key Files

| File | Purpose |
|------|---------|
| `PrivacySection.kt` | UI toggle for "Provide Location to Mesh" |
| `AndroidMeshLocationManager.kt` | Bridges phone GPS to mesh `sendPosition()` |
| `LocationRepositoryImpl.kt` | Gets phone location via `LocationManager` (30s interval) |
| `CommandSenderImpl.sendPosition()` | Builds and sends `POSITION_APP` packet |
| `MeshConnectionManagerImpl.kt` | Auto-starts location on connect if enabled |
| `MeshLocationUseCase.kt` | Use case to start/stop providing location |

---

## Three Factors of Node Positioning

A Meshtastic node's position is determined by **three independent factors** that interact based on priority rules enforced by the **node firmware** (not the Android app).

### The Three Factors

| Factor | What It Is | Where It Lives |
|--------|-----------|----------------|
| **1. Node Settings** | `gps_mode`, `fixed_position` — tell the firmware **which source to trust** | `PositionConfig` in node memory |
| **2. Internal GPS** | Physical GPS chip on the board — reads satellites | Hardware module, tagged `LOC_INTERNAL` |
| **3. Phone Coordinates** | `POSITION_APP` packet sent every ~30s | Tagged `LOC_EXTERNAL`, overwritten on each update |

### Scenario Matrix

| # | `gps_mode` | `fixed_position` | Phone Sends Coordinates | **Result on Node** |
|---|-----------|-----------------|----------------------|----------------------|
| 1 | `DISABLED` | `false` | ✅ Yes | ✅ Node **accepts** phone coordinates as its position |
| 2 | `DISABLED` | `true` | ✅ Yes | ❌ Node **ignores** phone, uses fixed coordinates |
| 3 | `ENABLED` | `false` | ✅ Yes | ⚠️ **Conflict**: GPS chip overwrites phone coordinates on each fix |
| 4 | `ENABLED` | `true` | ✅ Yes | ❌ Node ignores **both GPS and phone** — only fixed coordinates |
| **5** | **`NOT_PRESENT`** | **`false`** | **✅ Yes** | **✅ Node accepts phone coordinates — phone is the only source** |
| 6 | `NOT_PRESENT` | `true` | ✅ Yes | ❌ Node ignores phone, uses fixed coordinates |
| 7 | `DISABLED` | `false` | ❌ No | ❌ Node has **no position at all** |
| 8 | `ENABLED` | `false` | ❌ No | ✅ Node uses its own GPS chip |

### Priority Hierarchy (Firmware-Level)

```
┌─────────────────────────────────────────────────────────┐
│  1. fixed_position = true  (HIGHEST priority)           │
│     → Ignores GPS chip AND phone coordinates             │
│     → Uses only the statically set coordinates            │
├─────────────────────────────────────────────────────────┤
│  2. gps_mode = ENABLED  (MEDIUM priority)               │
│     → GPS chip is active, its data takes precedence       │
│     → Phone coordinates are accepted but overwritten      │
│       by the next GPS fix                                 │
├─────────────────────────────────────────────────────────┤
│  3. gps_mode = DISABLED / NOT_PRESENT  (LOWEST)         │
│     → GPS chip is off or absent                           │
│     → Phone coordinates become the PRIMARY source         │
└─────────────────────────────────────────────────────────┘
```

### Key Code Check

```kotlin
// CommandSenderImpl.kt (line 182)
// App updates local UI ONLY if fixed_position is NOT set
if (localConfig.value.position?.fixed_position != true) {
    nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
}

// But the POSITION_APP packet is ALWAYS sent, regardless of fixed_position
packetHandler.sendToRadio(buildMeshPacket(...))
```

**Important:** This check only affects the **app's UI**. The node itself **always receives** the position packet.

### What Each Factor Does

| Factor | Proto Field | Controls |
|--------|------------|----------|
| **Node GPS settings** | `PositionConfig.gps_mode` (enum: `DISABLED`/`ENABLED`/`NOT_PRESENT`) | Whether firmware polls the GPS hardware |
| **Fixed position** | `PositionConfig.fixed_position` (bool) + `AdminMessage.set_fixed_position` | Static coordinates stored on node |
| **Phone coordinates** | `Position.location_source = LOC_EXTERNAL` via `POSITION_APP` packet | Live coordinates from phone, sent every ~30s |

### LocSource Enum (Position Origin Tag)

| Value | Code | Meaning | Set By |
|-------|------|---------|--------|
| `LOC_UNSET` | `0` | Not set | — |
| `LOC_MANUAL` | `1` | Manually entered (fixed position) | User via UI |
| `LOC_INTERNAL` | `2` | Node's own GPS chip | GPS hardware on node |
| `LOC_EXTERNAL` | `3` | External source (phone/EUD) | Android app "Provide Location" |

> **Note:** The Android app **does not read** `location_source` for decision-making. It only **writes** it (`LOC_EXTERNAL` for phone coordinates). The firmware uses this tag to identify the origin of position data.

---

## Code-Level Deep Dive: Provide Location to Mesh

### Complete Data Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 1: UI (Jetpack Compose)                                          │
│  File: PrivacySection.kt                                                │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ SwitchListItem("Provide Location to Mesh")                         │  │
│  │   onClick → onToggleLocation(true)                                 │  │
│  │   LaunchedEffect checks:                                           │  │
│  │     1. permission granted? → if no, request                        │  │
│  │     2. GPS enabled? → if no, showToast("Location disabled")        │  │
│  │     3. all good → startProvideLocation()                           │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │ settingsViewModel.setProvideLocation(true)
                               │ settingsViewModel.startProvidingLocation()
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 2: ViewModel + UseCases                                          │
│  File: SettingsViewModel.kt                                             │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ setProvideLocation(value: Boolean)                                 │  │
│  │   → SetProvideLocationUseCase(myNodeNum, value)                    │  │
│  │     → uiPrefs.setShouldProvideNodeLocation(nodeNum, value)         │  │
│  │       → DataStore: "provide-location-{nodeNum}" = true             │  │
│  │                                                                  │  │
│  │ startProvidingLocation()                                           │  │
│  │   → MeshLocationUseCase.startProvidingLocation()                   │  │
│  │     → radioController.startProvideLocation()                       │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 3: RadioController → MeshService (AIDL bridge)                   │
│  File: AndroidRadioControllerImpl.kt                                    │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ startProvideLocation()                                             │  │
│  │   → serviceRepository.meshService?.startProvideLocation()          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  File: MeshService.kt (AIDL implementation)                             │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ startProvideLocation()                                             │  │
│  │   → locationManager.start(serviceScope) {                          │  │
│  │       commandSender.sendPosition(it)                               │  │
│  │     }                                                              │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 4: AndroidMeshLocationManager                                    │
│  File: AndroidMeshLocationManager.kt                                    │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ start(scope, sendPositionFn)                                       │  │
│  │   1. Check hasLocationPermission()                                 │  │
│  │   2. Subscribe to locationRepository.getLocations()                │  │
│  │   3. For each Location:                                            │  │
│  │      sendPositionFn(ProtoPosition(                                 │  │
│  │        latitude_i  = Position.degI(location.latitude),             │  │
│  │        longitude_i = Position.degI(location.longitude),            │  │
│  │        altitude    = mslAltitude,                                  │  │
│  │        altitude_hae = elipsoidAltitude,                            │  │
│  │        time        = epochSeconds,                                 │  │
│  │        ground_speed = speed,                                       │  │
│  │        ground_track = bearing,                                     │  │
│  │        location_source = LOC_EXTERNAL  ← marks as phone GPS        │  │
│  │      ))                                                            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 5: LocationRepositoryImpl (Android LocationManager)              │
│  File: LocationRepositoryImpl.kt                                        │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ getLocations(): Flow<Location>                                     │  │
│  │   → callbackFlow {                                                 │  │
│  │       LocationRequestCompat.Builder(30_000L)  // 30s interval      │  │
│  │         .setMinUpdateDistanceMeters(0f)                            │  │
│  │         .setQuality(QUALITY_HIGH_ACCURACY)                         │  │
│  │       Provider selection:                                          │  │
│  │         API >= 31: FUSED_PROVIDER                                  │  │
│  │         API < 31:  GPS_PROVIDER + NETWORK_PROVIDER                 │  │
│  │       LocationListenerCompat { trySend(location) }                 │  │
│  │     }                                                              │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 6: CommandSenderImpl.sendPosition()                              │
│  File: CommandSenderImpl.kt                                             │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ sendPosition(pos, destNum, wantResponse)                           │  │
│  │   1. myNum = nodeManager.myNodeNum                                 │  │
│  │   2. idNum = destNum ?: myNum  (default: self)                     │  │
│  │   3. if localConfig.position.fixed_position != true:               │  │
│  │        nodeManager.handleReceivedPosition(myNum, myNum, pos)       │  │
│  │   4. packetHandler.sendToRadio(buildMeshPacket(                    │  │
│  │        to       = idNum,                                           │  │
│  │        channel  = 0,                                               │  │
│  │        priority = BACKGROUND,                                      │  │
│  │        decoded  = Data(                                            │  │
│  │          portnum  = POSITION_APP,      ← PortNum.POSITION_APP      │  │
│  │          payload  = pos.encode(),      ← protobuf ByteString       │  │
│  │          want_response = false                                     │  │
│  │        )                                                           │  │
│  │      ))                                                            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Layer 7: BLE / Radio Interface                                         │
│  PacketHandler → NordicBleInterface → BLE characteristic write          │
│  → Meshtastic Device receives decoded Position packet                   │
└──────────────────────────────────────────────────────────────────────────┘
```

### Key Code Points

#### 1. Persistence (per-node setting)

```kotlin
// UiPrefsImpl.kt
private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"

override fun setShouldProvideNodeLocation(nodeNum: Int, value: Boolean) {
    scope.launch { 
        dataStore.edit { 
            it[booleanPreferencesKey(provideLocationKey(nodeNum))] = value 
        } 
    }
}
```

The setting is **bound to the specific node number**, so different nodes can have different location-sharing preferences.

#### 2. Auto-start on Connection

```kotlin
// MeshConnectionManagerImpl.kt (lines 120-131)
nodeRepository.myNodeInfo.onEach { myNodeEntity ->
    if (myNodeEntity != null) {
        uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
            .onEach { shouldProvide ->
                if (shouldProvide) {
                    locationManager.start(scope) { pos -> commandSender.sendPosition(pos) }
                } else {
                    locationManager.stop()
                }
            }.launchIn(scope)
    }
}.launchIn(scope)
```

If the user previously enabled "Provide Location", it **automatically resumes** on every connection to that node.

#### 3. Position Conversion (Phone → Proto)

```kotlin
// AndroidMeshLocationManager.kt
ProtoPosition(
    latitude_i    = Position.degI(location.latitude),   // degrees × 10⁷
    longitude_i   = Position.degI(location.longitude),  // degrees × 10⁷
    altitude      = mslAltitude?.toInt(),               // mean sea level
    altitude_hae  = location.altitude.toInt(),          // height above ellipsoid
    time          = (location.time.milliseconds.inWholeSeconds).toInt(),
    ground_speed  = location.speed.toInt(),
    ground_track  = location.bearing.toInt(),
    location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
)
```

**`Position.degI()`** converts latitude/longitude from degrees to **integer format** (× 10⁷), as required by the protobuf `Position` message.

**`LOC_EXTERNAL`** marks this position as coming from an **external GPS source** (the phone), not the node's built-in GPS.

#### 4. Packet Construction

```kotlin
// CommandSenderImpl.kt (lines 176-200)
override fun sendPosition(pos: ProtoPosition, destNum: Int?, wantResponse: Boolean) {
    val myNum = nodeManager.myNodeNum ?: return
    val idNum = destNum ?: myNum  // sends to self (connected node) by default

    // Update local node position ONLY if fixed_position is NOT set
    if (localConfig.value.position?.fixed_position != true) {
        nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
    }

    packetHandler.sendToRadio(buildMeshPacket(
        to       = idNum,
        channel  = if (destNum == null) 0 else nodeManager.nodeDBbyNodeNum[destNum]?.channel ?: 0,
        priority = MeshPacket.Priority.BACKGROUND,
        decoded  = Data(
            portnum     = PortNum.POSITION_APP,
            payload     = pos.encode().toByteString(),
            want_response = wantResponse,
        ),
    ))
}
```

**Important:** The position packet is sent as a **decoded** (unencrypted) `POSITION_APP` message, not an admin command. The node receives it as a normal position update.

---

## Testing Notes

- GPS configuration is handled through the standard `setConfig` admin flow
- The device responds with the updated config, which is saved locally via `LocalConfigDataSource`
- No dedicated unit tests exist for `PositionConfig` serialization/deserialization — consider adding tests for the `GpsMode` enum round-trip
- Phone location sharing logic (`AndroidMeshLocationManager`) also lacks unit tests — consider adding tests for the `LOC_EXTERNAL` position flow

---

*Generated: 2026-04-11*
