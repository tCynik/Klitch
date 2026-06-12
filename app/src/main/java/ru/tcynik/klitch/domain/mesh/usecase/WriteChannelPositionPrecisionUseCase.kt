package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class WriteChannelPositionPrecisionUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(destNum: Int, channelIndex: Int, precision: Int) =
        repository.writeChannelPositionPrecision(destNum, channelIndex, precision)
}
