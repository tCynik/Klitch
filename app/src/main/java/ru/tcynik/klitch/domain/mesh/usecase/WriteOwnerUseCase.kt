package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class WriteOwnerUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(longName: String, shortName: String) =
        repository.writeOwner(longName, shortName)
}
