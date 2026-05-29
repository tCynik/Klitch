package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import java.util.Base64

class SetContourActiveUseCaseTest {

    private val repository: ContourRepository = mockk(relaxed = true)
    private val useCase = SetContourActiveUseCase(repository)

    private val psk = byteArrayOf(0x01)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    private fun makeContour(id: String, isActive: Boolean = true): Contour {
        val hash = ContourHash.compute("test", psk)
        return Contour(
            id = ContourId(id),
            name = "test",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    @Test
    fun `emergency ID — no-op`() = runTest {
        useCase(DefaultContour.ID, true)

        coVerify(exactly = 0) { repository.saveContour(any()) }
        coVerify(exactly = 0) { repository.getPrimaryContourId() }
    }

    @Test
    fun `primary ID deactivate — no-op`() = runTest {
        val id = DefaultActiveContour.ID
        coEvery { repository.getPrimaryContourId() } returns id
        coEvery { repository.observeContours() } returns flowOf(listOf(makeContour(id.value, isActive = true)))

        useCase(id, false)

        coVerify(exactly = 0) { repository.saveContour(any()) }
    }

    @Test
    fun `regular ID found — saves contour with isActive updated to true`() = runTest {
        val id = "00000000-0000-0000-0000-000000000099"
        val contour = makeContour(id, isActive = false)
        coEvery { repository.getPrimaryContourId() } returns DefaultActiveContour.ID
        coEvery { repository.observeContours() } returns flowOf(listOf(contour))

        useCase(ContourId(id), true)

        coVerify(exactly = 1) {
            repository.saveContour(match { it.id == ContourId(id) && it.isActive })
        }
    }

    @Test
    fun `regular ID found — saves contour with isActive updated to false`() = runTest {
        val id = "00000000-0000-0000-0000-000000000099"
        val contour = makeContour(id, isActive = true)
        coEvery { repository.getPrimaryContourId() } returns DefaultActiveContour.ID
        coEvery { repository.observeContours() } returns flowOf(listOf(contour))

        useCase(ContourId(id), false)

        coVerify(exactly = 1) {
            repository.saveContour(match { it.id == ContourId(id) && !it.isActive })
        }
    }

    @Test
    fun `regular ID not found — saveContour not called`() = runTest {
        coEvery { repository.getPrimaryContourId() } returns DefaultActiveContour.ID
        coEvery { repository.observeContours() } returns flowOf(emptyList())

        useCase(ContourId("unknown"), true)

        coVerify(exactly = 0) { repository.saveContour(any()) }
    }
}
