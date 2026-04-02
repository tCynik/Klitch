package ru.tcynik.mymesh1.data.mesh.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.mymesh1.mesh.model.MeshUser
import org.meshtastic.proto.HardwareModel
import ru.tcynik.mymesh1.mesh.repository.CommandSender
import ru.tcynik.mymesh1.mesh.repository.MeshRouter
import ru.tcynik.mymesh1.mesh.repository.NodeRepository

class MeshConfigRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
) : MeshConfigRepository {

    override fun requestDeviceConfig() {
        meshRouter.configFlowManager.triggerWantConfig()
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
            val primaryChannel = channelSet.settings.firstOrNull()

            MeshDeviceConfigModel(
                longName = ourNode.user.long_name,
                shortName = ourNode.user.short_name,
                loraPreset = loraConfig?.modem_preset?.name ?: "",
                txPowerDbm = loraConfig?.tx_power?.takeIf { it > 0 }?.toString() ?: "auto",
                region = loraConfig?.region?.name ?: "",
                channelName = primaryChannel?.name?.ifBlank { "LongFast" } ?: "LongFast",
                pskMasked = if (primaryChannel?.psk?.size != null && primaryChannel.psk.size > 0)
                    "••••••••" else "none",
            )
        }
}
