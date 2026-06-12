package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class SetProvideLocationUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(nodeNum: Int, provide: Boolean) =
        repository.setProvideLocation(nodeNum, provide)
}
