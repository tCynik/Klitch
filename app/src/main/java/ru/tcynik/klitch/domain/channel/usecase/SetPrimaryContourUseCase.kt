package ru.tcynik.klitch.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.model.meshtasticChannelName
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase

class SetPrimaryContourUseCase(
    private val contourRepository: ContourRepository,
    private val writeChannel: WriteChannelUseCase,
) {
    suspend operator fun invoke(contourId: ContourId) {
        contourRepository.setPrimaryContour(contourId)
        val name: String
        val psk: String
        if (contourId == DefaultContour.ID) {
            name = DefaultContour.CHANNEL_NAME
            psk = DefaultContour.OPEN_PSK
        } else {
            val contour = contourRepository.observeContours().first().find { it.id == contourId } ?: return
            name = meshtasticChannelName(contour)
            psk = contour.transport.meshtastic.psk
        }
        writeChannel(0, name, psk)
    }
}
