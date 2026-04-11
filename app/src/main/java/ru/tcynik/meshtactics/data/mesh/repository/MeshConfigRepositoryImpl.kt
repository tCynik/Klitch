package ru.tcynik.meshtactics.data.mesh.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.ModuleSettings
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshChannelModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.mesh.model.MeshUser
import ru.tcynik.meshtactics.mesh.model.Position
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.MeshRouter
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.UiPrefs

class MeshConfigRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val uiPrefs: UiPrefs,
    private val context: Context,
) : MeshConfigRepository {

    override fun requestDeviceConfig() {
        meshRouter.configFlowManager.triggerWantConfig()
    }

    override fun writeChannel(index: Int, name: String, pskBase64: String) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
        val pskBytes = if (pskBase64.isBlank()) ByteArray(0)
                       else Base64.decode(pskBase64.trim(), Base64.DEFAULT)
        val channel = Channel(
            index = index,
            settings = ChannelSettings(name = name, psk = pskBytes.toByteString()),
            role = if (index == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
        )
        meshRouter.actionHandler.handleSetChannel(Channel.ADAPTER.encode(channel), myNodeNum)
    }

    override fun writeOwner(longName: String, shortName: String) {
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
        meshRouter.actionHandler.handleSetOwner(meshUser, myNodeNum)
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
            Log.i("MeshConfigRepo", "DBG combine: myNodeNum=${myNodeInfo?.myNodeNum} ourNode=${ourNode?.user?.long_name ?: "null"} nodeDBsize=${nodeDB.size} loraRegion=${config.lora?.region} channels=${channelSet.settings.size}")
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
