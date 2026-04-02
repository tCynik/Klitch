package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository

class RequestDeviceConfigUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke() = repository.requestDeviceConfig()
}
