package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.repository.LastConnectedDeviceRepository

class GetLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) {
    operator fun invoke(): MeshDeviceModel? = repository.get()
}
