package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class WriteGpsModeUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(gpsMode: GpsMode) = repository.writeGpsMode(gpsMode)
}
