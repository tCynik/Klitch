package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.EnableNodePositionBroadcastReadyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class GpsBroadcastCoordinator(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase,
    private val observeEmergencyMode: ObserveEmergencyModeUseCase,
    private val enableNodePositionBroadcastReady: EnableNodePositionBroadcastReadyUseCase,
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wasConnected = false

    init {
        scope.launch {
            observeConnectionStatus(NoParams).collect { status ->
                val isConnected = status is MeshConnectionStatus.Connected
                if (!wasConnected && isConnected) {
                    applyGpsBroadcastSettings()
                }
                wasConnected = isConnected
            }
        }
    }

    private suspend fun applyGpsBroadcastSettings() {
        val sosActive = observeEmergencyMode().first()
        val broadcastEnabled = observeGpsBroadcastEnabled().first()
        if (sosActive || !broadcastEnabled) {
            disableNodePositionBroadcast()
        } else {
            enableNodePositionBroadcastReady()
        }
    }
}
