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

class SetPrimaryContourUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val useCase = SetPrimaryContourUseCase(contourRepository, writeChannel)

    private val psk = byteArrayOf(0x01, 0x02)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    private fun makeContour(id: ContourId, name: String): Contour {
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = id,
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    @Test
    fun `saves primary id and writes channel 0 for regular contour`() = runTest {
        val contour = makeContour(DefaultActiveContour.ID, DefaultActiveContour.DISPLAY_NAME)
        coEvery { contourRepository.observeContours() } returns flowOf(listOf(DefaultContour.asContour(), contour))

        useCase(DefaultActiveContour.ID)

        coVerify(exactly = 1) { contourRepository.setPrimaryContour(DefaultActiveContour.ID) }
        coVerify(exactly = 1) { writeChannel(0, DefaultActiveContour.CHANNEL_NAME, pskBase64) }
    }

    @Test
    fun `Emergency id — writes LongFast to slot 0`() = runTest {
        coEvery { contourRepository.observeContours() } returns flowOf(listOf(DefaultContour.asContour()))

        useCase(DefaultContour.ID)

        coVerify(exactly = 1) { contourRepository.setPrimaryContour(DefaultContour.ID) }
        coVerify(exactly = 1) { writeChannel(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
    }

    @Test
    fun `contour not found — saves id but skips writeChannel`() = runTest {
        coEvery { contourRepository.observeContours() } returns flowOf(emptyList())

        useCase(ContourId("missing"))

        coVerify(exactly = 1) { contourRepository.setPrimaryContour(ContourId("missing")) }
        coVerify(exactly = 0) { writeChannel(any(), any(), any()) }
    }
}
