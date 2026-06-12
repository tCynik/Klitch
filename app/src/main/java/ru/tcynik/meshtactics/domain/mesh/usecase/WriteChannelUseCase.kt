package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class WriteChannelUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(
        index: Int,
        name: String,
        pskBase64: String,
        positionPrecision: Int = ChannelPositionPrecision.ENABLED,
    ) = repository.writeChannel(index, name, pskBase64, positionPrecision)
}
