package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class WritePositionConfigUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(
        destNum: Int,
        gpsMode: GpsMode,
        broadcastSecs: Int,
        smartEnabled: Boolean,
        smartMinDist: Int,
        flags: Int,
        gpsUpdateIntervalSecs: Int? = null,
        smartMinIntervalSecs: Int? = null,
    ) = repository.writePositionConfig(
        destNum, gpsMode, broadcastSecs, smartEnabled, smartMinDist, flags, gpsUpdateIntervalSecs, smartMinIntervalSecs,
    )
}
