package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class RemoveFixedPositionUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(destNum: Int) =
        repository.removeFixedPosition(destNum)
}
