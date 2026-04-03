package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class RequestDeviceConfigUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke() = repository.requestDeviceConfig()
}
