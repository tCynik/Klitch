package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import java.util.Base64

class ActivateExclusiveContourUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val useCase = ActivateExclusiveContourUseCase(contourRepository, writeChannel)

    private val psk = byteArrayOf(0x0A)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    private fun makeContour(id: ContourId, name: String, isActive: Boolean = true): Contour {
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = id,
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    @Test
    fun `sets primary, deactivates others, writes slots 0 1 and clears 2-7`() = runTest {
        val exclusiveId = ContourId("00000000-0000-0000-0000-000000000099")
        val exclusive = makeContour(exclusiveId, "Race")
        val other = makeContour(DefaultActiveContour.ID, "Basic")
        val emergency = DefaultContour.asContour()
        coEvery { contourRepository.observeContours() } returns flowOf(listOf(emergency, other, exclusive))

        useCase(exclusiveId)

        coVerify(exactly = 1) { contourRepository.setPrimaryContour(exclusiveId) }
        coVerify(exactly = 1) { contourRepository.saveContour(other.copy(isActive = false)) }
        coVerify(exactly = 0) { contourRepository.saveContour(match { it.id == DefaultContour.ID }) }
        coVerify(exactly = 1) { writeChannel(0, "Race", pskBase64) }
        coVerify(exactly = 1) { writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
        (2..7).forEach { slot ->
            coVerify(exactly = 1) { writeChannel(slot, "", "") }
        }
    }
}
