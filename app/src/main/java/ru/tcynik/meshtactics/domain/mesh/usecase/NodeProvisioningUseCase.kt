package ru.tcynik.meshtactics.domain.mesh.usecase

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

private const val TAG = "NodeProvisioning"

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
        Log.d(TAG, "provision() started")
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val user = observeAppUser(NoParams).first()

        contours.forEach { contour ->
            when (val r = resolveSlot(contour, nodeChannels)) {
                is SlotResolution.FreeSlot -> {
                    Log.d(TAG, "  writeChannel slot=${r.slot} name='${contour.name}'")
                    writeChannel(r.slot, contour.name, contour.transport.meshtastic.psk)
                }
                else -> Log.d(TAG, "  skip '${contour.name}' — already synced or no free slot")
            }
        }

        if (user.displayName.isNotBlank()) {
            Log.d(TAG, "  writeOwner longName='${user.displayName}'")
            val deviceConfig = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }
            writeOwner(user.displayName, deviceConfig?.shortName ?: "")
        }
        Log.d(TAG, "provision() done")
    }
}
