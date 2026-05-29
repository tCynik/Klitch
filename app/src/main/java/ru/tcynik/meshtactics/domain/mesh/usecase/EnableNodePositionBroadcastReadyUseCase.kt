package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class EnableNodePositionBroadcastReadyUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke() = repository.enableNodePositionBroadcastReady()
}
