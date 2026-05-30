package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class WriteOwnerUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(longName: String, shortName: String) =
        repository.writeOwner(longName, shortName)
}
