package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

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
    ) = repository.writePositionConfig(destNum, gpsMode, broadcastSecs, smartEnabled, smartMinDist, flags)
}
