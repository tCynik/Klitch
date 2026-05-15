package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.NodeSecurityModel

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
    fun writeOwner(longName: String, shortName: String)
    fun writeChannel(index: Int, name: String, pskBase64: String)

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
    fun writeChannelPositionPrecision(destNum: Int, channelIndex: Int, precision: Int)
    fun removeFixedPosition(destNum: Int)

    /** Configures the connected node for active geo broadcast (position_broadcast_secs=60, precision=13). */
    fun enableNodePositionBroadcastReady()

    /** Disables position broadcast on the connected node (position_broadcast_secs=MAX). */
    fun disableNodePositionBroadcast()

    fun rebootNode()
}
