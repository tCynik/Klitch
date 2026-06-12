package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class DisableNodePositionBroadcastUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke() = repository.disableNodePositionBroadcast()
}
