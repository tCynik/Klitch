package ru.tcynik.klitch.data.mesh.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import androidx.core.content.ContextCompat
import ru.tcynik.klitch.domain.logger.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.ModuleSettings
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigModel
import ru.tcynik.klitch.domain.mesh.model.MeshChannelModel
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.klitch.domain.mesh.model.NodeSecurityModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.mesh.model.MeshUser
import ru.tcynik.klitch.mesh.model.Node
import ru.tcynik.klitch.mesh.model.Position
import ru.tcynik.klitch.mesh.repository.CommandSender
import ru.tcynik.klitch.mesh.repository.MeshRouter
import ru.tcynik.klitch.mesh.repository.NodeRepository
import ru.tcynik.klitch.mesh.repository.PacketHandler
import ru.tcynik.klitch.mesh.repository.UiPrefs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

    private var settingsEditOpen = false
    private val settingsEditMutex = Mutex()

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

    override suspend fun beginSettingsEdit(): Boolean = settingsEditMutex.withLock {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "beginSettingsEdit: myNodeNum unavailable")
            return@withLock false
        }
        settingsEditOpen = false
        commandSender.setSessionPasskey(ByteString.EMPTY)
        packetHandler.prepareForAdminBurst()
        logger.i("Contour", "session_passkey: beginSettingsEdit start nodeNum=$myNodeNum")

        val beginPacketId = meshRouter.actionHandler.handleBeginEditSettings(myNodeNum)
        if (beginPacketId == 0) {
            logger.e("Contour", "session_passkey MISSING: begin_edit_settings not sent")
            return@withLock false
        }

        if (!awaitAdminPacket("beginSettingsEdit/begin", beginPacketId)) {
            logger.w(
                "Contour",
                "beginSettingsEdit: begin TX timeout id=${beginPacketId.toUInt()} — " +
                    "node may be rebooting or BLE session stale; aborting passkey",
            )
            settingsEditOpen = false
            return@withLock false
        }

        val passkey = acquireSessionPasskey(myNodeNum)
        if (passkey != null && passkey.size > 0) {
            settingsEditOpen = true
            logger.i("Contour", "session_passkey acquired size=${passkey.size}")
            true
        } else {
            settingsEditOpen = false
            logger.e("Contour", "session_passkey MISSING: acquireSessionPasskey — no passkey")
            false
        }
    }

    private fun snapshotSessionPasskey(): ByteString? =
        commandSender.sessionPasskeyFlow.value.takeIf { it.size > 0 }

    private suspend fun waitForSessionPasskey(label: String, timeout: Duration): ByteString? {
        snapshotSessionPasskey()?.let {
            logger.i("Contour", "session_passkey $label: already present size=${it.size}")
            return it
        }
        val waitStartMs = System.currentTimeMillis()
        val deadlineMs = waitStartMs + timeout.inWholeMilliseconds
        while (System.currentTimeMillis() < deadlineMs) {
            if (commandSender.nodeRebootedAfter(waitStartMs)) {
                logger.w("Contour", "session_passkey $label: aborted — node rebooted during wait")
                return null
            }
            delay(PASSKEY_POLL_INTERVAL_MS)
            snapshotSessionPasskey()?.let {
                logger.i("Contour", "session_passkey $label: received size=${it.size}")
                return it
            }
        }
        logger.w("Contour", "session_passkey $label: timeout ${timeout.inWholeSeconds}s")
        return null
    }

    private suspend fun acquireSessionPasskey(myNodeNum: Int): ByteString? {
        snapshotSessionPasskey()?.let { return it }

        // begin_edit_settings returns routing ACK only — passkey comes in get_channel_response.
        delay(BEGIN_SETTLE_DELAY_MS)

        repeat(PASSKEY_ATTEMPTS) { attempt ->
            val attemptNum = attempt + 1
            snapshotSessionPasskey()?.let { return it }

            val channelReqId = commandSender.generatePacketId()
            logger.i(
                "Contour",
                "session_passkey: get_channel reqId=${channelReqId.toUInt()} attempt=$attemptNum/$PASSKEY_ATTEMPTS",
            )
            meshRouter.actionHandler.handleGetRemoteChannel(channelReqId, myNodeNum, 0)

            val passkey = waitForSessionPasskey("beginSettingsEdit/getChannelResponse", PASSKEY_RESPONSE_TIMEOUT)
            if (passkey != null) return passkey

            logger.w(
                "Contour",
                "beginSettingsEdit: get_channel no passkey reqId=${channelReqId.toUInt()}",
            )

            logger.w("Contour", "beginSettingsEdit: passkey attempt $attemptNum/$PASSKEY_ATTEMPTS failed")
            if (attempt < PASSKEY_ATTEMPTS - 1) delay(PASSKEY_RETRY_DELAY_MS)
        }

        return requestPasskeyViaGetOwner(myNodeNum)
    }

    private suspend fun requestPasskeyViaGetOwner(myNodeNum: Int): ByteString? {
        snapshotSessionPasskey()?.let { return it }

        val ownerReqId = commandSender.generatePacketId()
        logger.i("Contour", "session_passkey: get_owner reqId=${ownerReqId.toUInt()} fallback")
        meshRouter.actionHandler.handleGetRemoteOwner(ownerReqId, myNodeNum)

        return waitForSessionPasskey("beginSettingsEdit/getOwnerResponse", PASSKEY_RESPONSE_TIMEOUT)
            .also { passkey ->
                if (passkey == null) {
                    logger.w("Contour", "beginSettingsEdit: get_owner no passkey reqId=${ownerReqId.toUInt()}")
                }
            }
    }

    private fun canWriteSettings(): Boolean =
        settingsEditOpen && commandSender.sessionPasskeyFlow.value.size > 0

    override suspend fun commitSettingsEdit() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "commitSettingsEdit: myNodeNum unavailable")
            return
        }
        logger.i("Node", "commitSettingsEdit: committing channel changes nodeNum=$myNodeNum — firmware reboot expected")
        if (!canWriteSettings()) {
            logger.w("Contour", "commitSettingsEdit: edit session not open")
            return
        }
        val packetId = meshRouter.actionHandler.handleCommitEditSettings(myNodeNum)
        awaitAdminPacket("commitSettingsEdit", packetId)
        settingsEditOpen = false
    }

    override suspend fun writeChannel(
        index: Int,
        name: String,
        pskBase64: String,
        positionPrecision: Int,
    ) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Contour", "writeChannel: myNodeNum unavailable, slot=$index name='$name'")
            return
        }
        val channel = buildChannel(index, name, pskBase64, positionPrecision)
        val passkeySize = commandSender.sessionPasskeyFlow.value.size
        logger.i("Contour", "session_passkey writeChannel slot=$index name='$name' passkeySize=$passkeySize settingsEditOpen=$settingsEditOpen")
        if (!canWriteSettings()) {
            logger.w("Contour", "writeChannel: edit session not open slot=$index name='$name'")
            return
        }
        val packetId = meshRouter.actionHandler.handleSetChannel(Channel.ADAPTER.encode(channel), myNodeNum)
        awaitAdminPacket("writeChannel slot=$index name='$name'", packetId)
    }

    override suspend fun writeOwner(longName: String, shortName: String) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Node", "writeOwner: myNodeNum unavailable longName='$longName'")
            return
        }
        logger.i("Node", "writeOwner: set_owner longName='$longName' nodeNum=$myNodeNum — firmware reboot expected")
        if (!canWriteSettings()) {
            logger.w("Node", "writeOwner: edit session not open longName='$longName'")
            return
        }
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

    private fun buildChannel(index: Int, name: String, pskBase64: String, positionPrecision: Int): Channel {
        val pskBytes = if (pskBase64.isBlank()) ByteArray(0)
                       else Base64.decode(pskBase64.trim(), Base64.DEFAULT)
        return Channel(
            index = index,
            settings = ChannelSettings(
                name = name,
                psk = pskBytes.toByteString(),
                module_settings = ModuleSettings(position_precision = positionPrecision),
            ),
            role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
        )
    }

    private suspend fun awaitAdminPacket(label: String, packetId: Int): Boolean {
        if (packetId == 0) {
            logger.w("Contour", "$label: packet not sent")
            return false
        }
        val delivered = packetHandler.awaitPacketSendResult(packetId, ADMIN_PACKET_TIMEOUT)
        if (!delivered) {
            logger.w("Contour", "$label: delivery timeout id=${packetId.toUInt()}")
        }
        return delivered
    }

    override fun observeLocationConfig(nodeNum: Int): Flow<LocationConfigModel> =
        combine(
            uiPrefs.shouldProvideNodeLocation(nodeNum),
            meshRouter.configHandler.localConfig,
            commandSender.channelSetFlow,
            uiPrefs.desiredGpsMode(nodeNum),
        ) { shouldProvide, localConfig, channelSet, desiredGpsModeOrdinal ->
            val posConfig = localConfig.position
            val precision = channelSet.settings.firstOrNull()
                ?.module_settings?.position_precision ?: 32
            // Show the pending override (if any) instead of the stale node value — the toggle
            // shouldn't snap back to the old gps_mode until the user confirms sync.
            val desiredGpsMode = desiredGpsModeOrdinal?.let { GpsMode.entries.getOrNull(it) }
            LocationConfigModel(
                provideLocationToMesh = shouldProvide,
                hasLocationPermission = hasLocationPermission(),
                gpsMode = desiredGpsMode ?: posConfig?.gps_mode.toDomain(),
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

    override fun observeDesiredGpsMode(nodeNum: Int): Flow<GpsMode?> =
        uiPrefs.desiredGpsMode(nodeNum).map { it?.let { ord -> GpsMode.entries.getOrNull(ord) } }

    override fun setDesiredGpsMode(nodeNum: Int, mode: GpsMode?) {
        uiPrefs.setDesiredGpsMode(nodeNum, mode?.ordinal)
    }

    override suspend fun getDesiredGpsMode(): GpsMode? {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return null
        return observeDesiredGpsMode(myNodeNum).first()
    }

    override suspend fun getGpsMode(): GpsMode? =
        withTimeoutOrNull(5_000L) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!.gps_mode.toDomain()
        }

    override fun writeGpsMode(gpsMode: GpsMode) {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Node", "writeGpsMode: myNodeNum unavailable")
            return
        }
        logger.i("Node", "writeGpsMode: destNum=$destNum gpsMode=$gpsMode — firmware reboot expected")
        val current = meshRouter.configHandler.localConfig.value.position ?: Config.PositionConfig()
        val updated = current.copy(gps_mode = gpsMode.toProto())
        val payload = Config.ADAPTER.encode(Config(position = updated))
        meshRouter.actionHandler.handleSetConfig(payload, destNum)
    }

    override fun writePositionConfig(
        destNum: Int,
        gpsMode: GpsMode,
        broadcastSecs: Int,
        smartEnabled: Boolean,
        smartMinDist: Int,
        flags: Int,
    ) {
        logger.i("Node", "writePositionConfig: destNum=$destNum gpsMode=$gpsMode broadcastSecs=$broadcastSecs — firmware reboot expected")
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
        logger.i("Node", "writeChannelPositionPrecision: destNum=$destNum channelIndex=$channelIndex precision=$precision — firmware reboot expected")
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

    override fun setFixedPosition(lat: Double, lon: Double, altMeters: Int) {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        commandSender.setFixedPosition(destNum, Position(lat, lon, altMeters))
    }

    override fun removeFixedPosition(destNum: Int) {
        commandSender.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    override fun removeOwnFixedPosition() {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        commandSender.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    override suspend fun prepareNodeForAppDrivenBroadcast() {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val localConfig = withTimeoutOrNull(POSITION_CONFIG_WAIT_MS) {
            meshRouter.configHandler.localConfig.first { it.position != null }
        } ?: return
        val current = localConfig.position!!
        val powerSavingOff = localConfig.power?.is_power_saving != true
        if (current.position_broadcast_secs == GEO_BROADCAST_READY_SECS &&
            !current.position_broadcast_smart_enabled &&
            powerSavingOff
        ) {
            logger.d("Node", "prepareNodeForAppDrivenBroadcast: already configured, skip")
            return
        }
        logger.i("Node", "prepareNodeForAppDrivenBroadcast: destNum=$destNum broadcastSecs=$GEO_BROADCAST_READY_SECS — firmware reboot expected")
        val updated = current.copy(
            position_broadcast_secs = GEO_BROADCAST_READY_SECS,
            position_broadcast_smart_enabled = false,
        )
        val payload = Config.ADAPTER.encode(Config(position = updated))
        val packetId = meshRouter.actionHandler.handleSetConfig(payload, destNum)
        awaitAdminPacket("prepareNodeForAppDrivenBroadcast/position", packetId)
        localConfig.power?.takeIf { it.is_power_saving == true }?.let { power ->
            logger.i("Node", "prepareNodeForAppDrivenBroadcast: disabling is_power_saving for background BLE")
            val powerPayload = Config.ADAPTER.encode(Config(power = power.copy(is_power_saving = false)))
            val powerPacketId = meshRouter.actionHandler.handleSetConfig(powerPayload, destNum)
            awaitAdminPacket("prepareNodeForAppDrivenBroadcast/power", powerPacketId)
        }
    }

    override suspend fun disableNodePositionBroadcast() {
        val destNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val current = withTimeoutOrNull(POSITION_CONFIG_WAIT_MS) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!
        } ?: return
        if (current.position_broadcast_secs == GEO_BROADCAST_DISABLED_SECS && !current.position_broadcast_smart_enabled) {
            logger.d("Node", "disableNodePositionBroadcast: already disabled, skip")
            return
        }
        logger.i("Node", "disableNodePositionBroadcast: destNum=$destNum broadcastSecs=$GEO_BROADCAST_DISABLED_SECS — firmware reboot expected")
        val updated = current.copy(
            position_broadcast_secs = GEO_BROADCAST_DISABLED_SECS,
            position_broadcast_smart_enabled = false,
        )
        val payload = Config.ADAPTER.encode(Config(position = updated))
        val packetId = meshRouter.actionHandler.handleSetConfig(payload, destNum)
        awaitAdminPacket("disableNodePositionBroadcast", packetId)
    }

    override suspend fun getPositionBroadcastSecs(): Int? =
        withTimeoutOrNull(5_000L) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!.position_broadcast_secs
        }

    override suspend fun isPositionSmartBroadcastEnabled(): Boolean? =
        withTimeoutOrNull(5_000L) {
            meshRouter.configHandler.localConfig.first { it.position != null }.position!!.position_broadcast_smart_enabled
        }

    override fun rebootNode() {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Node", "rebootNode: myNodeNum unavailable")
            return
        }
        val requestId = commandSender.generatePacketId()
        logger.i("Node", "rebootNode: initiating reboot nodeNum=$myNodeNum requestId=${requestId.toUInt()}")
        meshRouter.actionHandler.handleRequestReboot(requestId, myNodeNum)
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
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: run {
            logger.w("Node", "regeneratePkcKeys: myNodeNum unavailable")
            return
        }
        logger.i("Node", "regeneratePkcKeys: clearing private_key nodeNum=$myNodeNum — firmware reboot expected")
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
        // Both constants write Int.MAX_VALUE — firmware never broadcasts autonomously in either state.
        // The behavioral difference is in the callers:
        //   READY   → prepareNodeForAppDrivenBroadcast() also disables is_power_saving
        //   DISABLED → disableNodePositionBroadcast() leaves is_power_saving unchanged
        // TODO: restore is_power_saving=true in disableNodePositionBroadcast() once the
        //       full power-state lifecycle (Phase 3+) is implemented.
        private const val GEO_BROADCAST_READY_SECS = Int.MAX_VALUE
        private const val GEO_BROADCAST_DISABLED_SECS = Int.MAX_VALUE
        private const val GEO_CHANNEL_PRECISION = 13
        private const val POSITION_CONFIG_WAIT_MS = 15_000L
        private const val PASSKEY_ATTEMPTS = 2
        private const val PASSKEY_POLL_INTERVAL_MS = 100L
        private const val PASSKEY_RETRY_DELAY_MS = 300L
        private const val BEGIN_SETTLE_DELAY_MS = 300L
        private val ADMIN_PACKET_TIMEOUT = 12.seconds
        private val PASSKEY_RESPONSE_TIMEOUT = 12.seconds
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
