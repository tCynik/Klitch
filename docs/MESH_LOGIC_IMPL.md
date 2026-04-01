# Mesh Logic Implementation Guide

This document describes what needs to be implemented for `MeshTestScreen` to work with a real
Meshtastic node. UI and data classes are already ready — this document covers only the logic layer.

---

## 1. BLE Layer (Android)

**File:** `app/src/main/java/ru/tcynik/mymesh1/data/ble/MeshtasticBleAdapter.kt`

### Requirements

| Method | Description |
|--------|-------------|
| `startScan(): Flow<BleDeviceUi>` | BLE device scanning with filter by Meshtastic Service UUID (`0x6BA4`) |
| `stopScan()` | Stop scanning |
| `connect(address: String): Flow<MeshConnectionStatusUi>` | Establish connection, GATT connect + service discovery |
| `disconnect()` | Close GATT connection |
| `observePackets(): Flow<ByteArray>` | Subscribe to notify-characteristic (`0x2AB9` — Meshtastic FromRadio) |
| `sendPacket(bytes: ByteArray)` | Write to write-characteristic (`0x2AB8` — Meshtastic ToRadio) |

### Required Permissions (AndroidManifest)
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### Foreground Service
A Foreground Service with type `connectedDevice` is required to maintain BLE connection in the background.

---

## 2. Meshtastic Protobuf Integration

**Add dependency** in `shared/build.gradle.kts`:
```kotlin
implementation("com.google.protobuf:protobuf-javalite:4.x.x")
// or Wire (Square):
implementation("com.squareup.wire:wire-runtime:5.x.x")
```

**Source of .proto files:**
[github.com/meshtastic/protobufs](https://github.com/meshtastic/protobufs)

Required files:
- `meshtastic/mesh.proto` — `MeshPacket`, `Data`, `User`, `Position`, `NodeInfo`
- `meshtastic/config.proto` — `Config.DeviceConfig`, `Config.LoRaConfig`
- `meshtastic/channel.proto` — `Channel`, `ChannelSettings`
- `meshtastic/telemetry.proto` — `Telemetry`, `DeviceMetrics`, `EnvironmentMetrics`
- `meshtastic/portnums.proto` — `PortNum` enum

**Location:** `shared/src/commonMain/proto/meshtastic/`

---

## 3. Packet Decoder

**File:** `shared/src/commonMain/kotlin/ru/tcynik/mymesh1/data/mesh/MeshPacketDecoder.kt`

```kotlin
interface MeshPacketDecoder {
    fun decode(bytes: ByteArray): DecodedPacket
}

sealed interface DecodedPacket {
    data class TextMessage(val from: String, val to: String, val text: String, val id: Int) : DecodedPacket
    data class NodeInfo(val node: MeshNodeUi) : DecodedPacket
    data class DeviceMetrics(val metrics: DeviceMetricsUi) : DecodedPacket
    data class DeviceConfig(val config: DeviceConfigUi) : DecodedPacket
    data class ChannelConfig(val config: ChannelConfigUi) : DecodedPacket
    data class Ack(val requestId: Int) : DecodedPacket
    data class Unknown(val portNum: Int, val rawHex: String) : DecodedPacket
}
```

---

## 4. New Use Cases

Place in `shared/src/commonMain/kotlin/ru/tcynik/mymesh1/domain/usecase/mesh/`

| Use Case | Input | Returns | ViewModel Method |
|----------|-------|---------|------------------|
| `ScanBleDevicesUseCase` | — | `Flow<List<BleDeviceUi>>` | `onScanClick()` |
| `ConnectToNodeUseCase` | `address: String` | `Flow<MeshConnectionStatusUi>` | `onConnectClick(address)` |
| `DisconnectFromNodeUseCase` | — | `suspend` | `onDisconnectClick()` |
| `ObserveIncomingPacketsUseCase` | — | `Flow<DecodedPacket>` | init in ViewModel |
| `SendTextMessageUseCase` | `text: String, toNodeId: String` | `suspend Result<Unit>` | `onSendClick()` |
| `ReadDeviceConfigUseCase` | — | `suspend Result<DeviceConfigUi>` | `onReadConfigClick()` |
| `WriteDeviceConfigUseCase` | `config: DeviceConfigUi` | `suspend Result<Unit>` | `onWriteConfigClick()` |
| `ObserveTelemetryUseCase` | — | `Flow<DeviceMetricsUi>` | `onRefreshTelemetryClick()` |
| `ObserveNodeListUseCase` | — | `Flow<List<MeshNodeUi>>` | init in ViewModel |

---

## 5. NodeRepository Extension

Add methods to `NodeRepository` interface:

```kotlin
// Already exists:
fun connectToNode(nodeId: String)   // TODO → implement via BleAdapter
fun disconnectFromNode(nodeId: String)

// Add:
fun observeConnectionStatus(): Flow<MeshConnectionStatusUi>
```

Create new repositories:

| Interface | Description |
|-----------|-------------|
| `MessageRepository` | send/receive text messages, store in SQLDelight |
| `ConfigRepository` | read and write device configuration |
| `TelemetryRepository` | store and subscribe to device metrics |

---

## 6. ViewModel → UseCase Mapping

Binding of stub methods `MeshTestViewModel` to use cases:

```
onScanClick()              → ScanBleDevicesUseCase → connectionTab.scannedDevices
onStopScanClick()          → cancel scanning coroutine
onConnectClick(address)    → ConnectToNodeUseCase → connectionStatus
onDisconnectClick()        → DisconnectFromNodeUseCase
onInputChange(text)        → update messagesTab.inputText (UI only, no UseCase)
onSendClick()              → SendTextMessageUseCase → new MeshMessageUi to list
onReadConfigClick()        → ReadDeviceConfigUseCase → configTab.deviceConfig
onEditConfigClick()        → toggle configTab.isEditing (UI only)
onWriteConfigClick()       → WriteDeviceConfigUseCase
onRefreshTelemetryClick()  → ObserveTelemetryUseCase → telemetryTab.deviceMetrics
onLogFilterChange(filter)  → update logTab.activeFilter (UI only)
onLogPauseToggle()         → toggle logTab.isPaused (UI only)
onTabSelected(tab)         → update selectedTab (UI only)
```

Packets from `ObserveIncomingPacketsUseCase` are distributed in `init {}` of ViewModel:
- `DecodedPacket.TextMessage` → add to `messagesTab.messages`
- `DecodedPacket.NodeInfo` → add/update in `telemetryTab.meshNodes`
- `DecodedPacket.DeviceMetrics` → update `telemetryTab.deviceMetrics`
- `DecodedPacket.Ack` → update message status to `Acked`
- All → add `LogEntryUi` to `logTab.entries`

---

## 7. State Mapping: Domain → UI

| Domain Model | UI Data Class | Mapper |
|--------------|---------------|--------|
| `MeshPacket` (proto) | `MeshMessageUi` | `MeshMessageUiMapper` |
| `NodeInfo` (proto) | `MeshNodeUi` | `MeshNodeUiMapper` |
| `DeviceMetrics` (proto) | `DeviceMetricsUi` | `DeviceMetricsUiMapper` |
| `Config.DeviceConfig` (proto) | `DeviceConfigUi` | `DeviceConfigUiMapper` |
| `Channel` (proto) | `ChannelConfigUi` | `ChannelConfigUiMapper` |

Place mappers in `app/src/main/java/ru/tcynik/mymesh1/presentation/feature/meshtest/mapper/`

---

## 8. Error Handling Strategy

| Error | State Transition |
|-------|------------------|
| BLE scan permission denied | `MeshConnectionStatusUi.Error("BLE permission denied")` |
| GATT connect timeout | `MeshConnectionStatusUi.Error("Connection timeout")` |
| GATT disconnect unexpected | `MeshConnectionStatusUi.Disconnected` + log to `LogTab` |
| Proto decode error | skip packet, add `LogEntryUi` with direction=System |
| Send timeout (no ACK for 30s) | `MessageStatus.Failed` for specific message |
| Config write rejected | emit to `configTab` error field (add `error: String?`) |

---

## 9. DI — What to Add to Koin Modules

**`androidModule` (app):**
```kotlin
single { MeshtasticBleAdapter(get()) }
```

**`commonModule` (shared):**
```kotlin
single<MessageRepository> { MessageRepositoryImpl(get(), get()) }
single<ConfigRepository> { ConfigRepositoryImpl(get()) }
single<TelemetryRepository> { TelemetryRepositoryImpl(get()) }
factory { ScanBleDevicesUseCase(get()) }
factory { ConnectToNodeUseCase(get()) }
factory { DisconnectFromNodeUseCase(get()) }
factory { ObserveIncomingPacketsUseCase(get()) }
factory { SendTextMessageUseCase(get()) }
factory { ReadDeviceConfigUseCase(get()) }
factory { WriteDeviceConfigUseCase(get()) }
factory { ObserveTelemetryUseCase(get()) }
factory { ObserveNodeListUseCase(get()) }
```

**`presentationModule` (app):**
```kotlin
// MeshTestViewModel already registered
viewModel { MeshTestViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
```

---

## 10. SQLDelight — New Tables

Add to `shared/src/commonMain/sqldelight/`:

```sql
-- Message.sq
CREATE TABLE Message (
    id          INTEGER PRIMARY KEY,
    from_node   TEXT NOT NULL,
    to_node     TEXT NOT NULL,
    text        TEXT NOT NULL,
    timestamp   INTEGER NOT NULL,
    status      TEXT NOT NULL  -- PENDING | SENT | ACKED | FAILED
);

-- Telemetry.sq
CREATE TABLE Telemetry (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    node_id         TEXT NOT NULL,
    battery_level   INTEGER,
    voltage         REAL,
    chan_util        REAL,
    air_util_tx      REAL,
    uptime_seconds  INTEGER,
    timestamp       INTEGER NOT NULL
);
```
