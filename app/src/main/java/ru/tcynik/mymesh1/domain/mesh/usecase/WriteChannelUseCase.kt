package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository

class WriteChannelUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(index: Int, name: String, pskBase64: String) =
        repository.writeChannel(index, name, pskBase64)
}
