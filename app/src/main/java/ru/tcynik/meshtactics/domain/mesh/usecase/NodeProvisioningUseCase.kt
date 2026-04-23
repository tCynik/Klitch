package ru.tcynik.meshtactics.domain.mesh.usecase

import android.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class NodeProvisioningUseCase(
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val writeOwner: WriteOwnerUseCase,
) {
    suspend fun provision() {
        val channels = observeLogicalChannels(NoParams).first()
        val user = observeAppUser(NoParams).first()

        channels.forEach { channel ->
            val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
                ?: return@forEach
            val slot = binding.resolvedSlot ?: return@forEach
            val pskBase64 = Base64.encodeToString(binding.psk, Base64.NO_WRAP)
            writeChannel(slot, channel.metadata.name, pskBase64)
        }

        if (user.displayName.isNotBlank()) {
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }
    }
}
