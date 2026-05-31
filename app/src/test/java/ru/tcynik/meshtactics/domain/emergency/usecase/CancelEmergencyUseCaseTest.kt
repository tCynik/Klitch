package ru.tcynik.meshtactics.domain.emergency.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.meshtactics.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

class CancelEmergencyUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val setPrimaryContour: SetPrimaryContourUseCase = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val appUserRepository: AppUserRepository = mockk()
    private val sendChatMessage: SendChatMessageUseCase = mockk(relaxed = true)
    private val broadcast: EmergencyPositionBroadcastRepository = mockk(relaxed = true)

    private val useCase = CancelEmergencyUseCase(
        contourRepository = contourRepository,
        setPrimaryContour = setPrimaryContour,
        writeChannel = writeChannel,
        appUserRepository = appUserRepository,
        sendChatMessage = sendChatMessage,
        broadcast = broadcast,
    )

    @Test
    fun `останавливает трансляцию геопозиции`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        coEvery { contourRepository.getPreSosPrimaryId() } returns DefaultActiveContour.ID

        useCase()

        verify(exactly = 1) { broadcast.stop() }
    }

    @Test
    fun `отправляет сообщение что всё в порядке`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        coEvery { contourRepository.getPreSosPrimaryId() } returns DefaultActiveContour.ID

        var capturedParams: SendChatMessageParams? = null
        coEvery { sendChatMessage.invoke(any()) } answers { capturedParams = firstArg() }

        useCase()

        assertTrue(capturedParams!!.text.contains("Иван"))
        assertTrue(capturedParams!!.text.contains("всё в порядке"))
        assertEquals("^all", capturedParams!!.contactId)
        assertEquals(0, capturedParams!!.channel)
    }

    @Test
    fun `восстанавливает Primary, снимает SOS и очищает preSos`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        coEvery { contourRepository.getPreSosPrimaryId() } returns DefaultActiveContour.ID

        useCase()

        coVerify(exactly = 1) { setPrimaryContour(DefaultActiveContour.ID) }
        coVerify(exactly = 1) {
            writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK, ChannelPositionPrecision.DISABLED)
        }
        coVerify(exactly = 1) { contourRepository.setSosMode(false) }
        coVerify(exactly = 1) { contourRepository.savePreSosPrimaryId(null) }
    }

    @Test
    fun `использует Неизвестный при пустом callsign`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser(""))
        coEvery { contourRepository.getPreSosPrimaryId() } returns DefaultActiveContour.ID

        var capturedText = ""
        coEvery { sendChatMessage.invoke(any()) } answers {
            capturedText = firstArg<SendChatMessageParams>().text
        }

        useCase()

        assertTrue(capturedText.contains("Неизвестный"))
    }

    @Test
    fun `порядок — стоп, сообщение, Primary, SOS off, preSos clear`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        coEvery { contourRepository.getPreSosPrimaryId() } returns DefaultActiveContour.ID

        useCase()

        coVerifyOrder {
            broadcast.stop()
            sendChatMessage.invoke(any())
            setPrimaryContour(DefaultActiveContour.ID)
            writeChannel(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK, ChannelPositionPrecision.DISABLED)
            contourRepository.setSosMode(false)
            contourRepository.savePreSosPrimaryId(null)
        }
    }
}
