package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.LastConnectedDeviceRepository

class GetLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) {
    operator fun invoke(): MeshDeviceModel? = repository.get()
}
