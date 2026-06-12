package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class IsPositionSmartBroadcastEnabledUseCase(private val repository: MeshConfigRepository) {
    suspend operator fun invoke(): Boolean? = repository.isPositionSmartBroadcastEnabled()
}
