# MyNodeInfo Network Reception Logic — Debug Reference

> Created: 2026-04-02  
> Purpose: description of the existing implementation for debugging the local device data reception chain

---

## 1. Data Models

### 1.1 Domain model — `MyNodeInfo`
**File:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/model/MyNodeInfo.kt`

```kotlin
@Parcelize
data class MyNodeInfo(
    val myNodeNum: Int,          // Our node number in the mesh
    val hasGPS: Boolean,         // GPS capability (always false when created from network)
    val model: String?,          // Hardware model (e.g. "tbeam-1.1")
    val firmwareVersion: String?,
    val couldUpdate: Boolean,    // Whether the app contains a firmware image we could install
    val shouldUpdate: Boolean,   // Whether the device firmware is outdated
    val currentPacketId: Long,
    val messageTimeoutMsec: Int, // Always 300000 ms
    val minAppVersion: Int,      // Minimum required app version
    val maxChannels: Int,        // Always 8
    val hasWifi: Boolean,
    val channelUtilization: Float, // Always 0f when created from network
    val airUtilTx: Float,          // Always 0f when created from network
    val deviceId: String?,
    val pioEnv: String?,
) : Parcelable
```

### 1.2 Protobuf model — `ProtoMyNodeInfo`
**File:** `mesh/src/main/proto/meshtastic/mesh.proto`  
**Generated:** `mesh/build/generated/source/wire/debug/org/meshtastic/proto/MyNodeInfo.kt`

Key protobuf fields:
- `my_node_num` → mapped to `myNodeNum`
- `min_app_version` → mapped to `minAppVersion`
- `device_id` → mapped to `deviceId`
- `pio_env` → mapped to `pioEnv`

Additional fields (from `DeviceMetadata`, a separate packet):
- `hw_model` → mapped to `model`
- `firmware_version` → mapped to `firmwareVersion`
- `hasWifi` → mapped to `hasWifi`

### 1.3 DB entity — `MyNodeEntity`
**File:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/database/entity/MyNodeEntity.kt`

Room entity for the `my_node` table. Has a `toMyNodeInfo()` method for converting back to the domain model. On conversion, `hasGPS`, `channelUtilization`, and `airUtilTx` are always set to `false`/`0f`.

---

## 2. Handshake Protocol

Local node data arrives as part of a two-stage initialization protocol.

**Constants:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/repository/HandshakeConstants.kt`
```
CONFIG_NONCE    = 69420  — Stage 1: request device configuration
NODE_INFO_NONCE = 69421  — Stage 2: request node database
```

### Stage 1: Config-Only
1. App connects to the radio (BLE)
2. `MeshConnectionManagerImpl.handleConnected()` → calls `startConfigOnly()`
3. `startConfigOnly()` sends `ToRadio(want_config_id = 69420)` to the radio
4. Radio begins responding with a stream of `FromRadio` packets:
   - `my_info` — MyNodeInfo data
   - `metadata` — DeviceMetadata (hardware model, firmware)
   - `config` — device configuration
   - `module_config` — module configuration
   - `channel` — channel data
   - `config_complete_id = 69420` — Stage 1 completion signal

### Stage 2: NodeInfo-Only
1. After receiving `config_complete_id = 69420`, after a 100 ms delay
2. Sends `ToRadio(want_config_id = 69421)`
3. Radio responds with a stream of `node_info` packets (all nodes in the mesh)
4. Ends with `config_complete_id = 69421`

### Handshake Stall Guard
- Timeout: **30 seconds** per stage
- On stall: one retry attempt
- On second stall: full connection reset (`onConnectionChanged(Disconnected)`)

---

## 3. Network Data Reception Chain

```
[Radio device (BLE)]
      │  FromRadio protobuf bytes
      ▼
MeshMessageProcessorImpl.handleFromRadio(bytes, myNodeNum)
  File: mesh/data/manager/MeshMessageProcessorImpl.kt:85
  │  FromRadio.ADAPTER.decode(bytes) — protobuf parsing
  │  processFromRadio(proto, myNodeNum)
  ▼
  ├─ if proto.packet != null → handleReceivedMeshPacket() (not our path)
  └─ otherwise → fromRadioDispatcher.handleFromRadio(proto)
      │
      ▼
FromRadioPacketHandlerImpl.handleFromRadio(proto)
  File: mesh/data/manager/FromRadioPacketHandlerImpl.kt:42
  │  when {
  │    myInfo != null       → configFlowManager.handleMyInfo(myInfo)      ◄── our path
  │    metadata != null     → configFlowManager.handleLocalMetadata(metadata)
  │    nodeInfo != null     → configFlowManager.handleNodeInfo(nodeInfo)
  │    configCompleteId != null → configFlowManager.handleConfigComplete(id)
  │    ...
  │  }
  ▼
MeshConfigFlowManagerImpl.handleMyInfo(myInfo: ProtoMyNodeInfo)
  File: mesh/data/manager/MeshConfigFlowManagerImpl.kt:145
  │  rawMyNodeInfo = myInfo                  ← stores raw protobuf
  │  nodeManager.myNodeNum = myInfo.my_node_num  ← sets our node number
  │  regenMyNodeInfo(lastMetadata)           ← builds domain model
  │  + clears radioConfig/channels (async)
  ▼
MeshConfigFlowManagerImpl.regenMyNodeInfo(metadata: DeviceMetadata?)
  File: mesh/data/manager/MeshConfigFlowManagerImpl.kt:172
  │  Builds SharedMyNodeInfo from rawMyNodeInfo + metadata:
  │    myNodeNum = my_node_num
  │    hasGPS = false                       ← HARDCODED
  │    model = metadata?.hw_model?.name     ← null if metadata not yet received!
  │    firmwareVersion = metadata?.firmware_version
  │    couldUpdate = false                  ← HARDCODED
  │    shouldUpdate = false                 ← HARDCODED
  │    currentPacketId = commandSender.getCurrentPacketId()
  │    messageTimeoutMsec = 300000          ← HARDCODED
  │    minAppVersion = min_app_version
  │    maxChannels = 8                      ← HARDCODED
  │    hasWifi = metadata?.hasWifi == true
  │    channelUtilization = 0f              ← HARDCODED
  │    airUtilTx = 0f                       ← HARDCODED
  │    deviceId = device_id.utf8()
  │    pioEnv = pio_env.ifEmpty { null }
  │  newMyNodeInfo = mi
  ▼
  (metadata arrives as a separate packet)
MeshConfigFlowManagerImpl.handleLocalMetadata(metadata: DeviceMetadata)
  File: mesh/data/manager/MeshConfigFlowManagerImpl.kt:158
  │  lastMetadata = metadata
  │  regenMyNodeInfo(metadata)              ← called again with metadata!
  │                                            overwrites newMyNodeInfo
  ▼
  (when config_complete_id = 69420 is received)
MeshConfigFlowManagerImpl.handleConfigComplete(69420)
  → handleConfigOnlyComplete()
  File: mesh/data/manager/MeshConfigFlowManagerImpl.kt:84
  │  Checks newMyNodeInfo != null
  │  If null: calls regenMyNodeInfo(lastMetadata) once more (safety fallback)
  │  myNodeInfo = finalizedInfo             ← commits the final object
  │  connectionManager.onRadioConfigLoaded()
  │  delay(100ms) → sendHeartbeat() → startNodeInfoOnly()
  ▼
  (when config_complete_id = 69421 is received)
MeshConfigFlowManagerImpl.handleConfigComplete(69421)
  → handleNodeInfoComplete()
  File: mesh/data/manager/MeshConfigFlowManagerImpl.kt:120
  │  Processes accumulated NodeInfo (all mesh nodes)
  │  myNodeInfo?.let {
  │    nodeRepository.installConfig(it, entities)  ← writes to DB
  │  }
  │  nodeManager.setNodeDbReady(true)
  │  serviceRepository.setConnectionState(Connected)
  ▼
NodeRepositoryImpl.installConfig(mi: MyNodeInfo, nodes: List<Node>)
  File: mesh/data/repository/NodeRepositoryImpl.kt:189
  │  mi.toEntity() → MyNodeEntity            ← convert to DB entity
  │  nodeInfoWriteDataSource.installConfig(entity, nodeEntities)
  ▼
Room Database (table: my_node)
  │
  ▼
NodeRepositoryImpl.myNodeInfo: StateFlow<MyNodeInfo?>
  File: mesh/data/repository/NodeRepositoryImpl.kt:69
  │  nodeInfoReadDataSource.myNodeInfoFlow()  ← Flow from Room
  │    .map { it?.toMyNodeInfo() }            ← convert back to domain model
  │    .flowOn(dispatchers.io)
  │    .stateIn(scope, SharingStarted.Eagerly, null)
  ▼
UI (ViewModel, Composable)
```

---

## 4. Critical Behaviors and Potential Issues

### 4.1 Arrival order of my_info and metadata
`my_info` and `metadata` are two separate `FromRadio` packets. They can arrive in any order.

- If `my_info` arrives **before** metadata: `regenMyNodeInfo()` is called with `metadata = null`. Fields `model`, `firmwareVersion`, `hasWifi` will be `null`/`false`. When metadata arrives, `regenMyNodeInfo()` is called again and overwrites `newMyNodeInfo`.
- If metadata arrives **before** `my_info`: `handleLocalMetadata()` saves it to `lastMetadata`, but `regenMyNodeInfo()` skips execution (because `rawMyNodeInfo == null`). When `my_info` arrives, `regenMyNodeInfo(lastMetadata)` is called with full metadata already available.

**In both cases**, `handleConfigOnlyComplete()` has a safety check: if `newMyNodeInfo == null`, `regenMyNodeInfo(lastMetadata)` is called one more time.

### 4.2 DB write only happens at Stage 2
`nodeRepository.installConfig()` is called **only** in `handleNodeInfoComplete()`, i.e. only after Stage 2 completes. If Stage 2 never completes — data will not be persisted and the `myNodeInfo` StateFlow will remain `null`.

### 4.3 Internal state in MeshConfigFlowManagerImpl
The class maintains four variables:
```kotlin
private var rawMyNodeInfo: ProtoMyNodeInfo? = null   // raw protobuf from network
private var lastMetadata: DeviceMetadata? = null      // last received metadata
private var newMyNodeInfo: SharedMyNodeInfo? = null   // currently-building object
private var myNodeInfo: SharedMyNodeInfo? = null      // committed at Stage 1
```
`myNodeInfo` (the final one) is set in `handleConfigOnlyComplete()` and used for the DB write in `handleNodeInfoComplete()`.

### 4.4 Fields always hardcoded when created from network
| Field | Value | Note |
|-------|-------|------|
| `hasGPS` | `false` | Not determined from network |
| `couldUpdate` | `false` | Not supported in current implementation |
| `shouldUpdate` | `false` | Not supported in current implementation |
| `messageTimeoutMsec` | `300000` | 5 minutes, hardcoded |
| `maxChannels` | `8` | Hardcoded |
| `channelUtilization` | `0f` | Not present in my_info packet |
| `airUtilTx` | `0f` | Not present in my_info packet |

### 4.5 Config reset on handleMyInfo
When `my_info` is received, the following are cleared asynchronously:
```kotlin
radioConfigRepository.clearChannelSet()
radioConfigRepository.clearLocalConfig()
radioConfigRepository.clearLocalModuleConfig()
```
This prepares for receiving fresh config/channel packets in the current session.

---

## 5. Debug Checkpoints

| What to check | Where to look | Log message |
|---------------|---------------|-------------|
| `my_info` received | `MeshConfigFlowManagerImpl.handleMyInfo()` | `"MyNodeInfo received: $nodeNum"` |
| `metadata` received | `handleLocalMetadata()` | `"Local Metadata received: $fw"` |
| Domain object built | `regenMyNodeInfo()` | `"newMyNodeInfo updated: nodeNum=... model=... fw=..."` |
| Object committed | `handleConfigOnlyComplete()` | `"myNodeInfo committed successfully (nodeNum=...)"` |
| Build error | `regenMyNodeInfo()` catch | `"Failed to regenMyNodeInfo"` |
| Handshake stall | `handleConfigOnlyComplete()` | `"Handshake stall: Did not receive a valid MyNodeInfo before Stage 1 complete"` |
| Stage 2 complete | `handleNodeInfoComplete()` | `"NodeInfo complete (Stage 2)"` |
| DB write | `NodeRepositoryImpl.installConfig()` | — |

### Common failure scenarios:
1. **`myNodeInfo` StateFlow always null** → Stage 2 (`config_complete_id = 69421`) was never received, or `myNodeInfo` field in the Manager was null at the time of the DB write
2. **`model` and `firmwareVersion` are null** → `metadata` packet was not received, or arrived after Stage 1 completed
3. **Stale data after reconnect** → `rawMyNodeInfo`/`lastMetadata` are not reset on reconnect (no explicit reset logic)

---

## 6. Files Involved in the Chain

| File | Role |
|------|------|
| `mesh/model/MyNodeInfo.kt` | Domain model |
| `mesh/database/entity/MyNodeEntity.kt` | DB entity + converter to domain |
| `mesh/repository/HandshakeConstants.kt` | Protocol constants (69420, 69421) |
| `mesh/repository/MeshConfigFlowManager.kt` | Config manager interface |
| `mesh/data/manager/MeshMessageProcessorImpl.kt` | Decodes BLE bytes to protobuf |
| `mesh/data/manager/FromRadioPacketHandlerImpl.kt` | Routes by FromRadio packet type |
| `mesh/data/manager/MeshConfigFlowManagerImpl.kt` | Core logic for my_info/metadata handling |
| `mesh/data/manager/MeshConnectionManagerImpl.kt` | Connection management and handshake |
| `mesh/data/repository/NodeRepositoryImpl.kt` | StateFlow + DB write |
