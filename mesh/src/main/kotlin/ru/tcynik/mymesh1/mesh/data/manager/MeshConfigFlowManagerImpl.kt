/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ru.tcynik.mymesh1.mesh.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import okio.IOException
import org.koin.core.annotation.Single
import ru.tcynik.mymesh1.mesh.common.util.handledLaunch
import ru.tcynik.mymesh1.mesh.common.util.ioDispatcher
import ru.tcynik.mymesh1.mesh.model.ConnectionState
import ru.tcynik.mymesh1.mesh.repository.CommandSender
import ru.tcynik.mymesh1.mesh.repository.HandshakeConstants
import ru.tcynik.mymesh1.mesh.repository.MeshConfigFlowManager
import ru.tcynik.mymesh1.mesh.repository.MeshConnectionManager
import ru.tcynik.mymesh1.mesh.repository.NodeManager
import ru.tcynik.mymesh1.mesh.repository.NodeRepository
import ru.tcynik.mymesh1.mesh.repository.PacketHandler
import ru.tcynik.mymesh1.mesh.repository.PlatformAnalytics
import ru.tcynik.mymesh1.mesh.repository.RadioConfigRepository
import ru.tcynik.mymesh1.mesh.repository.ServiceBroadcasts
import ru.tcynik.mymesh1.mesh.repository.ServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import ru.tcynik.mymesh1.mesh.model.MyNodeInfo as SharedMyNodeInfo
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConfigFlowManagerImpl(
    private val nodeManager: NodeManager,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val analytics: PlatformAnalytics,
    private val commandSender: CommandSender,
    private val packetHandler: PacketHandler,
) : MeshConfigFlowManager {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val wantConfigDelay = 100L

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    private val newNodes = mutableListOf<NodeInfo>()
    override val newNodeCount: Int
        get() = newNodes.size

    private var rawMyNodeInfo: ProtoMyNodeInfo? = null
    private var lastMetadata: DeviceMetadata? = null
    private var newMyNodeInfo: SharedMyNodeInfo? = null
    private var myNodeInfo: SharedMyNodeInfo? = null

    override fun handleConfigComplete(configCompleteId: Int) {
        when (configCompleteId) {
            HandshakeConstants.CONFIG_NONCE -> handleConfigOnlyComplete()
            HandshakeConstants.NODE_INFO_NONCE -> handleNodeInfoComplete()
            else -> Logger.w { "Config complete id mismatch: $configCompleteId" }
        }
    }

    private fun handleConfigOnlyComplete() {
        Logger.i { "Config-only complete (Stage 1)" }
        if (newMyNodeInfo == null) {
            Logger.w {
                "newMyNodeInfo is still null at Stage 1 complete, attempting final regen with last known metadata"
            }
            regenMyNodeInfo(lastMetadata)
        }

        val finalizedInfo = newMyNodeInfo
        if (finalizedInfo == null) {
            Logger.e { "Handshake stall: Did not receive a valid MyNodeInfo before Stage 1 complete" }
        } else {
            myNodeInfo = finalizedInfo
            Logger.i { "myNodeInfo committed successfully (nodeNum=${finalizedInfo.myNodeNum})" }
            connectionManager.value.onRadioConfigLoaded()
        }

        scope.handledLaunch {
            delay(wantConfigDelay)
            sendHeartbeat()
            delay(wantConfigDelay)
            Logger.i { "Requesting NodeInfo (Stage 2)" }
            connectionManager.value.startNodeInfoOnly()
        }
    }

    private fun sendHeartbeat() {
        try {
            packetHandler.sendToRadio(ToRadio(heartbeat = Heartbeat()))
            Logger.d { "Heartbeat sent between nonce stages" }
        } catch (ex: IOException) {
            Logger.w(ex) { "Failed to send heartbeat; proceeding with node-info stage" }
        }
    }

    private fun handleNodeInfoComplete() {
        Logger.i { "NodeInfo complete (Stage 2)" }
        val entities = newNodes.map { info ->
            nodeManager.installNodeInfo(info, withBroadcast = false)
            nodeManager.nodeDBbyNodeNum[info.num]!!
        }
        newNodes.clear()

        scope.handledLaunch {
            val mi = myNodeInfo
            if (mi == null) {
                Logger.e { "handleNodeInfoComplete: myNodeInfo is null — installConfig skipped, StateFlow will remain null" }
            } else {
                Logger.i { "installConfig: writing nodeNum=${mi.myNodeNum} model=${mi.model} fw=${mi.firmwareVersion} nodes=${entities.size}" }
                nodeRepository.installConfig(mi, entities)
                Logger.i { "installConfig: DB write complete for nodeNum=${mi.myNodeNum}" }
                sendAnalytics(mi)
            }
            nodeManager.setNodeDbReady(true)
            nodeManager.setAllowNodeDbWrites(true)
            serviceRepository.setConnectionState(ConnectionState.Connected)
            serviceBroadcasts.broadcastConnection()
            connectionManager.value.onNodeDbReady()
        }
    }

    private fun sendAnalytics(mi: SharedMyNodeInfo) {
        analytics.setDeviceAttributes(mi.firmwareVersion ?: "unknown", mi.model ?: "unknown")
    }

    override fun handleMyInfo(myInfo: ProtoMyNodeInfo) {
        Logger.i { "MyNodeInfo received: ${myInfo.my_node_num}" }
        if (rawMyNodeInfo != null) {
            Logger.w { "handleMyInfo: rawMyNodeInfo already set (was ${rawMyNodeInfo?.my_node_num}), overwriting with ${myInfo.my_node_num}" }
        }
        rawMyNodeInfo = myInfo
        nodeManager.myNodeNum = myInfo.my_node_num
        regenMyNodeInfo(lastMetadata)

        scope.handledLaunch {
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    override fun handleLocalMetadata(metadata: DeviceMetadata) {
        Logger.i { "Local Metadata received: fw=${metadata.firmware_version} hw_model=${metadata.hw_model} hasWifi=${metadata.hasWifi}" }
        lastMetadata = metadata
        regenMyNodeInfo(metadata)
    }

    override fun handleNodeInfo(info: NodeInfo) {
        newNodes.add(info)
    }

    override fun triggerWantConfig() {
        connectionManager.value.startConfigOnly()
    }

    private fun regenMyNodeInfo(metadata: DeviceMetadata? = null) {
        val myInfo = rawMyNodeInfo
        if (myInfo != null) {
            try {
                val mi =
                    with(myInfo) {
                        SharedMyNodeInfo(
                            myNodeNum = my_node_num,
                            hasGPS = false,
                            model =
                            when (val hwModel = metadata?.hw_model) {
                                null,
                                HardwareModel.UNSET,
                                -> null
                                else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                            },
                            firmwareVersion = metadata?.firmware_version?.takeIf { it.isNotBlank() },
                            couldUpdate = false,
                            shouldUpdate = false,
                            currentPacketId = commandSender.getCurrentPacketId() and 0xffffffffL,
                            messageTimeoutMsec = 300000,
                            minAppVersion = min_app_version,
                            maxChannels = 8,
                            hasWifi = metadata?.hasWifi == true,
                            channelUtilization = 0f,
                            airUtilTx = 0f,
                            deviceId = device_id.utf8(),
                            pioEnv = myInfo.pio_env.ifEmpty { null },
                        )
                    }
                if (metadata != null && metadata != DeviceMetadata()) {
                    scope.handledLaunch { nodeRepository.insertMetadata(mi.myNodeNum, metadata) }
                }
                newMyNodeInfo = mi
                Logger.d { "newMyNodeInfo updated: nodeNum=${mi.myNodeNum} model=${mi.model} fw=${mi.firmwareVersion}" }
            } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
                Logger.e(ex) { "Failed to regenMyNodeInfo" }
            }
        } else {
            Logger.v { "regenMyNodeInfo skipped: rawMyNodeInfo is null" }
        }
    }
}
