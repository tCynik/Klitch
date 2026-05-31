package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class GetPositionBroadcastSecsUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(): Int? = repository.getPositionBroadcastSecs()
}
