package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class GetPositionBroadcastSecsUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(): Int? = repository.getPositionBroadcastSecs()
}
