package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class PrepareNodeForAppDrivenBroadcastUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke() = repository.prepareNodeForAppDrivenBroadcast()
}
