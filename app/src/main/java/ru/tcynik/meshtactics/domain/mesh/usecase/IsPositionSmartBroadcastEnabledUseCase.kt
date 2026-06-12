package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class IsPositionSmartBroadcastEnabledUseCase(private val repository: MeshConfigRepository) {
    suspend operator fun invoke(): Boolean? = repository.isPositionSmartBroadcastEnabled()
}
