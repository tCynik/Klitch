package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class SetDesiredGpsModeUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(nodeNum: Int, mode: GpsMode?) = repository.setDesiredGpsMode(nodeNum, mode)
}
