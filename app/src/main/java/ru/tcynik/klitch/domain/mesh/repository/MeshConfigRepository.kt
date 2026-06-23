package ru.tcynik.klitch.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigModel
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.klitch.domain.mesh.model.NodeSecurityModel

interface MeshConfigRepository {

    // PKC key management
    fun isOwnPkcKeyBroken(): Boolean
    fun observeSecurityConfig(): Flow<NodeSecurityModel?>
    fun refreshKnownNodePublicKeys()
    fun refreshNodePublicKey(nodeNum: Int)
    fun regeneratePkcKeys()
    fun observeCallsignChanges(): Flow<Int>
    fun observeNodeChannels(): Flow<List<NodeChannelSlot>>
    fun observeDeviceConfig(): Flow<MeshDeviceConfigModel?>
    fun requestDeviceConfig()
    /** @return true if session passkey acquired and edit session is open */
    suspend fun beginSettingsEdit(): Boolean
    suspend fun commitSettingsEdit()
    suspend fun writeOwner(longName: String, shortName: String)
    suspend fun writeChannel(
        index: Int,
        name: String,
        pskBase64: String,
        positionPrecision: Int,
    )

    fun observeLocationConfig(nodeNum: Int): Flow<LocationConfigModel>
    fun setProvideLocation(nodeNum: Int, provide: Boolean)
    fun writePositionConfig(
        destNum: Int,
        gpsMode: GpsMode,
        broadcastSecs: Int,
        smartEnabled: Boolean,
        smartMinDist: Int,
        flags: Int,
    )

    /** User's desired `gps_mode` override for a node, set via NetworkSettings toggle. `null` = no override. */
    fun observeDesiredGpsMode(nodeNum: Int): Flow<GpsMode?>
    fun setDesiredGpsMode(nodeNum: Int, mode: GpsMode?)

    /** Desired `gps_mode` override for the connected node, or null if none set. */
    suspend fun getDesiredGpsMode(): GpsMode?

    /** Current `gps_mode` reported by the connected node's local config, or null if not yet loaded. */
    suspend fun getGpsMode(): GpsMode?

    /** Writes `gps_mode` only, leaving other position config fields untouched. Must run inside an edit session. */
    fun writeGpsMode(gpsMode: GpsMode)
    fun writeChannelPositionPrecision(destNum: Int, channelIndex: Int, precision: Int)
    fun setFixedPosition(lat: Double, lon: Double, altMeters: Int)
    fun removeFixedPosition(destNum: Int)
    fun removeOwnFixedPosition()

    /**
     * Silences firmware autonomous position broadcast and prepares the node for app-driven sending:
     * `position_broadcast_secs = Int.MAX_VALUE`, smart broadcast off, `is_power_saving = false`.
     * Waits for position config.
     */
    suspend fun prepareNodeForAppDrivenBroadcast()

    /** Disables position broadcast on the connected node (position_broadcast_secs=MAX). Waits for position config. */
    suspend fun disableNodePositionBroadcast()

    /** Returns the current position_broadcast_secs from local node config, or null if not yet loaded. */
    suspend fun getPositionBroadcastSecs(): Int?

    /** Returns true if position_broadcast_smart_enabled is set, false if not, null on timeout. */
    suspend fun isPositionSmartBroadcastEnabled(): Boolean?

    fun rebootNode()

    /** Requests fresh device telemetry (battery/voltage/channel util/uptime) from the connected node. */
    fun requestTelemetry()
}
