package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class RefreshNodePublicKeysUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke() = repository.refreshKnownNodePublicKeys()
}
