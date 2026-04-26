package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class NodeProvisioningUseCase(
    private val observeContours: ObserveContoursUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
) {
    suspend fun provision() {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val user = observeAppUser(NoParams).first()

        contours.forEach { contour ->
            val slot = when (val r = resolveSlot(contour, nodeChannels)) {
                is SlotResolution.AlreadySynced -> r.slot
                else -> return@forEach
            }
            writeChannel(slot, contour.name, contour.transport.meshtastic.psk)
        }

        if (user.displayName.isNotBlank()) {
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }
    }
}
