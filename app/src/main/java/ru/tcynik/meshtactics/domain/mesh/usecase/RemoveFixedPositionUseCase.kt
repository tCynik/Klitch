package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class RemoveFixedPositionUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(destNum: Int) =
        repository.removeFixedPosition(destNum)
}
