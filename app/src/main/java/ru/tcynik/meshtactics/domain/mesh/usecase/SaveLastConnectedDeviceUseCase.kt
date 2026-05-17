package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.LastConnectedDeviceRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class SaveLastConnectedDeviceUseCase(
    private val repository: LastConnectedDeviceRepository,
) : UseCase<MeshDeviceModel, Unit>() {
    override suspend fun invoke(params: MeshDeviceModel) = repository.save(params)
}
