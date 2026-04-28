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
            val slot = when (val r = resolveSlot(contour, nodeChannels)) {
                is SlotResolution.AlreadySynced -> r.slot
                else -> {
                    Log.d(TAG, "  skip '${contour.name}' — not AlreadySynced")
                    return@forEach
                }
            }
            Log.d(TAG, "  writeChannel slot=$slot name='${contour.name}' ← WILL TRIGGER NODE REBOOT")
            writeChannel(slot, contour.name, contour.transport.meshtastic.psk)
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
