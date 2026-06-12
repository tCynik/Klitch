package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.repository.LastConnectedDeviceRepository
import ru.tcynik.klitch.domain.usecase.base.UseCase

class SaveLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) : UseCase<MeshDeviceModel, Unit>() {
    override suspend fun invoke(params: MeshDeviceModel) = repository.save(params)
}
