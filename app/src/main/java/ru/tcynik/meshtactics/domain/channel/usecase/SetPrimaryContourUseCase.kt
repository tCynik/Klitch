package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase

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
