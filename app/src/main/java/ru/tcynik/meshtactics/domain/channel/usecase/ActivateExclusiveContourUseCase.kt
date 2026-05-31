package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.first
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase

class ActivateExclusiveContourUseCase(
    private val contourRepository: ContourRepository,
    private val writeChannel: WriteChannelUseCase,
) {
    suspend operator fun invoke(contourId: ContourId) {
        contourRepository.setPrimaryContour(contourId)
        val contours = contourRepository.observeContours().first()
        contours
            .filter { !it.isEmergency && it.id != contourId }
            .forEach { contourRepository.saveContour(it.copy(isActive = false)) }

        val exclusive = contours.find { it.id == contourId } ?: return
        val name = if (exclusive.isEmergency) DefaultContour.CHANNEL_NAME else meshtasticChannelName(exclusive)
        val psk = if (exclusive.isEmergency) DefaultContour.OPEN_PSK else exclusive.transport.meshtastic.psk
        writeChannel(0, name, psk)
        writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK)
        for (slot in 2..7) {
            writeChannel(slot, "", "")
        }
    }
}
