package ru.tcynik.klitch.domain.gps.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

/** Resolves [PositionSourceMode] for the currently connected node from its `gps_mode` config. */
class ObservePositionSourceModeUseCase(
    private val meshNetworkRepository: MeshNetworkRepository,
    private val meshConfigRepository: MeshConfigRepository,
) : FlowUseCase<NoParams, PositionSourceMode>() {
    override fun invoke(params: NoParams): Flow<PositionSourceMode> =
        meshNetworkRepository.observeOurNode().flatMapLatest { node ->
            if (node == null) {
                flowOf(PositionSourceMode.PHONE_GPS)
            } else {
                meshConfigRepository.observeLocationConfig(node.num).map { config ->
                    when (config.gpsMode) {
                        GpsMode.ENABLED -> PositionSourceMode.NODE_GPS
                        GpsMode.DISABLED, GpsMode.NOT_PRESENT -> PositionSourceMode.PHONE_GPS
                    }
                }
            }
        }
}
