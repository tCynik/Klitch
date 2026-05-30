package ru.tcynik.meshtactics.data.mesh.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.core.content.ContextCompat
import ru.tcynik.meshtactics.domain.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.ModuleSettings
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshChannelModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.NodeSecurityModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.mesh.model.MeshUser
import ru.tcynik.meshtactics.mesh.model.Node
import ru.tcynik.meshtactics.mesh.model.Position
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.MeshRouter
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.PacketHandler
import ru.tcynik.meshtactics.mesh.repository.UiPrefs
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class MeshConfigRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val packetHandler: PacketHandler,
    private val uiPrefs: UiPrefs,
    private val context: Context,
    private val logger: Logger,
) : MeshConfigRepository {

    override fun observeNodeChannels(): Flow<List<NodeChannelSlot>> =
        commandSender.channelSetFlow.map { channelSet ->
            channelSet.settings.mapIndexed { index, settings ->
                NodeChannelSlot(
                    index = index,
                    name = settings.name,
                    psk = settings.psk.toByteArray(),
                    isEnabled = index == 0 || settings.psk.size > 0,
                    positionPrecision = settings.module_settings?.position_precision ?: 0,
                )
            }
        }

    override fun requestDeviceConfig() {
        meshRouter.configFlowManager.triggerWantConfig()
    }

    override suspend fun beginSettingsEdit() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "beginSettingsEdit: myNodeNum unavailable")
            return
        }
        val packetId = meshRouter.actionHandler.handleBeginEditSettings(myNodeNum)
        awaitAdminPacket("beginSettingsEdit", packetId)
    }

    override suspend fun commitSettingsEdit() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "commitSettingsEdit: myNodeNum unavailable")
            return
        }
        val packetId = meshRouter.actionHandler.handleCommitEditSettings(myNodeNum)
        awaitAdminPacket("commitSettingsEdit", packetId)
    }

    override suspend fun writeChannel(index: Int, name: String, pskBase64: String) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "writeChannel: myNodeNum unavailable, slot=$index name='$name'")
            return
        }
        val channel = buildChannel(index, name, pskBase64)
        val packetId = meshRouter.actionHandler.handleSetChannel(Channel.ADAPTER.encode(channel), myNodeNum)
        awaitAdminPacket("writeChannel slot=$index name='$name'", packetId)
    }

    override suspend fun writeOwner(longName: String, shortName: String) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val existingUser = nodeRepository.nodeDBbyNum.value[myNodeNum]?.user
        val meshUser = MeshUser(
            id = existingUser?.id ?: "",
            longName = longName,
            shortName = shortName,
            hwModel = existingUser?.hw_model ?: HardwareModel.UNSET,
            isLicensed = existingUser?.is_licensed ?: false,
            role = existingUser?.role?.value ?: 0,
        )
        val packetId = meshRouter.actionHandler.handleSetOwner(meshUser, myNodeNum)
        awaitAdminPacket("writeOwner longName='$longName'", packetId)
    }

    private fun buildChannel(index: Int, name: String, pskBase64: String): Channel {
        val pskBytes = if (pskBase64.isBlank()) ByteArray(0)
                       else Base64.decode(pskBase64.trim(), Base64.DEFAULT)
        val existing = commandSender.channelSetFlow.value.settings.getOrNull(index) ?: ChannelSettings()
        val nameChanged = existing.name != name
        val pskChanged = !existing.psk.toByteArray().contentEquals(pskBytes)
        val channelId = if (nameChanged || pskChanged || existing.id == 0) Random.nextInt() else existing.id
        val updatedSettings = existing.copy(
            name = name,
            psk = pskBytes.toByteString(),
            id = channelId,
            module_settings = ModuleSettings(
                position_precision = if (name == DefaultContour.CHANNEL_NAME && pskBase64.trim() == DefaultContour.OPEN_PSK) 0
                                    else CHANNEL_POSITION_PRECISION
            ),
        )
        return Channel(
            index = index,
            settings = updatedSettings,
            role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
        )
    }

    private suspend fun awaitAdminPacket(label: String, packetId: Int) {
        if (packetId == 0) {
            logger.w("Contour", "$label: packet not sent")
            return
        }
        val delivered = packetHandler.awaitPacketSendResult(packetId, ADMIN_PACKET_TIMEOUT)
        if (!delivered) {
            logger.w("Contour", "$label: delivery timeout id=${packetId.toUInt()}")
        }
    }

    override fun observeLocationConfig(nodeNum: Int): Flow<LocationConfigModel> =
        combine(
            uiPrefs.shouldProvideNodeLocation(nodeNum),
            meshRouter.configHandler.localConfig,
            commandSender.channelSetFlow,
        ) { shouldProvide, localConfig, channelSet ->
            val posConfig = localConfig.position
            val precision = channelSet.settings.firstOrNull()
                ?.module_settings?.position_precision ?: 32
            LocationConfigModel(
                provideLocationToMesh = shouldProvide,
                hasLocationPermission = hasLocationPermission(),
                gpsMode = posConfig?.gps_mode.toDomain(),
                fixedPositionEnabled = posConfig?.fixed_position ?: false,
                broadcastIntervalSecs = posConfig?.position_broadcast_secs?.takeIf { it > 0 } ?: 900,
                smartBroadcastEnabled = posConfig?.position_broadcast_smart_enabled ?: false,
                smartBroadcastMinDistanceM = posConfig?.broadcast_smart_minimum_distance ?: 0,
                positionFlags = posConfig?.position_flags ?: 0,
                primaryChannelPositionPrecision = precision,
            )
        }

    override fun setProvideLocation(nodeNum: Int, provide: Boolean) {
        uiPrefs.setShouldProvideNodeLocation(nodeNum, provide)
    }

    override fun writePositionConfig(
        destNum: Int,
        gpsMode: GpsMode,
        broadcastSecs: Int,
        smartEnabled: Boolean,
        smartMinDist: Int,
        flags: Int,
    ) {
        val current = meshRouter.configHandler.localConfig.value.position
            ?: Config.PositionConfig()
        val updated = current.copy(
            gps_mode = gpsMode.toProto(),
            position_broadcast_secs = broadcastSecs,
            position_broadcast_smart_enabled = smartEnabled,
            broadcast_smart_minimum_distance = smartMinDist,
            position_flags = flags,
        )
        val payload = Config.ADAPTER.encode(Config(position = updated))
        meshRouter.actionHandler.handleSetConfig(payload, destNum)
    }

    override fun writeChannelPositionPrecision(destNum: Int, channelIndex: Int, precision: Int) {
        val channelSet = commandSender.channelSetFlow.value
        val existing = channelSet.settings.getOrNull(channelIndex) ?: ChannelSettings()
        val updated = existing.copy(
            module_settings = (existing.module_settings ?: ModuleSettings()).copy(
                position_precision = precision,
            ),
        )
        val channel = Channel(
            index = channelIndex,
            settings = updated,
            role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
        )
        meshRouter.actionHandler.handleSetChannel(Channel.ADAPTER.encode(channel), destNum)
    }

    override fun removeFixedPosition(destNum: Int) {
        commandSender.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    override suspend fun enableNodePositionBroadcastReady() {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val current = withTimeoutOrNull(POSITION_CONFIG_WAIT_MS) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!
        } ?: return
        val updated = current.copy(
            position_broadcast_secs = GEO_BROADCAST_READY_SECS,
            position_broadcast_smart_enabled = false,
        )
        val payload = Config.ADAPTER.encode(Config(position = updated))
        meshRouter.actionHandler.handleSetConfig(payload, destNum)
    }

    override suspend fun disableNodePositionBroadcast() {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val current = withTimeoutOrNull(POSITION_CONFIG_WAIT_MS) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!
        } ?: return
        val updated = current.copy(
            position_broadcast_secs = GEO_BROADCAST_DISABLED_SECS,
            position_broadcast_smart_enabled = false,
        )
        val payload = Config.ADAPTER.encode(Config(position = updated))
        meshRouter.actionHandler.handleSetConfig(payload, destNum)
    }

    override fun rebootNode() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        meshRouter.actionHandler.handleRequestReboot(commandSender.generatePacketId(), myNodeNum)
    }

    override fun isOwnPkcKeyBroken(): Boolean {
        val sec = meshRouter.configHandler.localConfig.value.security ?: return true
        return sec.public_key.size == 0 || sec.public_key == Node.ERROR_BYTE_STRING
    }

    override fun refreshKnownNodePublicKeys() {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        nodeRepository.nodeDBbyNum.value.values
            .filter { it.num != myNum && it.lastHeard > 0 }
            .forEach { node -> commandSender.requestUserInfo(node.num) }
    }

    override fun refreshNodePublicKey(nodeNum: Int) {
        commandSender.requestUserInfo(nodeNum)
    }

    override fun regeneratePkcKeys() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val currentSec = meshRouter.configHandler.localConfig.value.security
            ?: Config.SecurityConfig()
        val resetSec = currentSec.copy(private_key = ByteString.EMPTY)
        val payload = Config.ADAPTER.encode(Config(security = resetSec))
        meshRouter.actionHandler.handleSetConfig(payload, myNodeNum)
    }

    override fun observeSecurityConfig(): Flow<NodeSecurityModel?> =
        meshRouter.configHandler.localConfig.map { localConfig ->
            val sec = localConfig.security ?: return@map null
            NodeSecurityModel(
                publicKeyHex = sec.public_key.hex(),
                hasKey = sec.public_key.size > 0,
                isMismatch = sec.public_key == Node.ERROR_BYTE_STRING,
            )
        }

    override fun observeCallsignChanges(): Flow<Int> = channelFlow {
        var prevNames = emptyMap<Int, String>()
        nodeRepository.nodeDBbyNum.collect { db ->
            val currNames = db.mapValues { (_, node) -> node.user.long_name }
            currNames.forEach { (num, name) ->
                val oldName = prevNames[num]
                if (oldName != null && oldName.isNotEmpty() && oldName != name) {
                    send(num)
                }
            }
            prevNames = currNames
        }
    }

    companion object {
        private const val GEO_BROADCAST_READY_SECS = 60
        private const val GEO_BROADCAST_DISABLED_SECS = Int.MAX_VALUE
        private const val GEO_CHANNEL_PRECISION = 13
        private const val CHANNEL_POSITION_PRECISION = 32
        private const val POSITION_CONFIG_WAIT_MS = 15_000L
        private val ADMIN_PACKET_TIMEOUT = 10.seconds
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Config.PositionConfig.GpsMode?.toDomain(): GpsMode = when (this) {
        Config.PositionConfig.GpsMode.ENABLED -> GpsMode.ENABLED
        Config.PositionConfig.GpsMode.NOT_PRESENT -> GpsMode.NOT_PRESENT
        else -> GpsMode.DISABLED
    }

    private fun GpsMode.toProto(): Config.PositionConfig.GpsMode = when (this) {
        GpsMode.DISABLED -> Config.PositionConfig.GpsMode.DISABLED
        GpsMode.ENABLED -> Config.PositionConfig.GpsMode.ENABLED
        GpsMode.NOT_PRESENT -> Config.PositionConfig.GpsMode.NOT_PRESENT
    }

    override fun observeDeviceConfig(): Flow<MeshDeviceConfigModel?> =
        combine(
            meshRouter.configHandler.localConfig,
            nodeRepository.nodeDBbyNum,
            nodeRepository.myNodeInfo,
            commandSender.channelSetFlow,
        ) { config, nodeDB, myNodeInfo, channelSet ->
            val ourNode = myNodeInfo?.myNodeNum?.let { nodeDB[it] }
            logger.i("Node", "observeDeviceConfig: myNodeNum=${myNodeInfo?.myNodeNum} ourNode=${ourNode?.user?.long_name ?: "null"} nodeDBsize=${nodeDB.size} loraRegion=${config.lora?.region} channels=${channelSet.settings.size}")
            if (ourNode == null) return@combine null

            val loraConfig = config.lora

            MeshDeviceConfigModel(
                longName = ourNode.user.long_name,
                shortName = ourNode.user.short_name,
                loraPreset = loraConfig?.modem_preset?.name ?: "",
                txPowerDbm = loraConfig?.tx_power?.takeIf { it > 0 }?.toString() ?: "auto",
                region = loraConfig?.region?.name ?: "",
                channels = channelSet.settings.mapIndexed { index, ch ->
                    MeshChannelModel(
                        index = index,
                        name = ch.name.ifBlank { if (index == 0) "LongFast" else "Channel ${index + 1}" },
                        pskBase64 = if (ch.psk.size > 0)
                            Base64.encodeToString(ch.psk.toByteArray(), Base64.NO_WRAP)
                        else "",
                    )
                },
            )
        }
}
