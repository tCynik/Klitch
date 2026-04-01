package ru.tcynik.mymesh1.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.mymesh1.mesh.repository.CommandSender
import ru.tcynik.mymesh1.mesh.repository.MeshRouter
import ru.tcynik.mymesh1.mesh.repository.NodeRepository

class MeshConfigRepositoryImpl(
    private val meshRouter: MeshRouter,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
) : MeshConfigRepository {

    override fun observeDeviceConfig(): Flow<MeshDeviceConfigModel?> =
        combine(
            meshRouter.configHandler.localConfig,
            nodeRepository.ourNodeInfo,
        ) { config, ourNode ->
            if (ourNode == null) return@combine null

            val loraConfig = config.lora
            val channelSet = commandSender.getCachedChannelSet()
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
