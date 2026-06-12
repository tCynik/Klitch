package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class RefreshNodePublicKeyUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke(nodeNum: Int) = repository.refreshNodePublicKey(nodeNum)
}
