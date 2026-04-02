package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository

class WriteOwnerUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(longName: String, shortName: String) =
        repository.writeOwner(longName, shortName)
}
