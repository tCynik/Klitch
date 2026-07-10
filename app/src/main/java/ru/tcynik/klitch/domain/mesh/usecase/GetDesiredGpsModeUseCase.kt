package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class GetDesiredGpsModeUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(): GpsMode? = repository.getDesiredGpsMode()
}
