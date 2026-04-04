package ru.tcynik.meshtactics.data.mesh.repository

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.HardwareModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshChannelModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.mesh.model.MeshUser
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.MeshRouter
import ru.tcynik.meshtactics.mesh.repository.NodeRepository

class MeshConfigRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
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
