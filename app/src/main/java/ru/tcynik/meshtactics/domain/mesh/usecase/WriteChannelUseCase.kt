package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class WriteChannelUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(index: Int, name: String, pskBase64: String) =
        repository.writeChannel(index, name, pskBase64)
}
