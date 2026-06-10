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
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

class CancelEmergencyUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val appUserRepository: AppUserRepository = mockk()
    private val sendChatMessage: SendChatMessageUseCase = mockk(relaxed = true)
    private val broadcast: EmergencyPositionBroadcastRepository = mockk(relaxed = true)

    private val useCase = CancelEmergencyUseCase(
        contourRepository = contourRepository,
        appUserRepository = appUserRepository,
        sendChatMessage = sendChatMessage,
        broadcast = broadcast,
        channelSlotResolver = channelSlotResolver,
    )

    @Test
    fun `останавливает трансляцию геопозиции`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { channelSlotResolver.hashToSlot } returns mapOf(DefaultContour.CHANNEL_HASH to 1)

        useCase()

        verify(exactly = 1) { broadcast.stop() }
    }

    @Test
    fun `отправляет сообщение что всё в порядке`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { channelSlotResolver.hashToSlot } returns mapOf(DefaultContour.CHANNEL_HASH to 1)

        var capturedParams: SendChatMessageParams? = null
        coEvery { sendChatMessage.invoke(any()) } answers { capturedParams = firstArg() }

        useCase()

        assertTrue(capturedParams!!.text.contains("Иван"))
        assertTrue(capturedParams!!.text.contains("всё в порядке"))
        assertEquals("^all", capturedParams!!.contactId)
        assertEquals(1, capturedParams!!.channel)
    }

    @Test
    fun `снимает SOS`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { channelSlotResolver.hashToSlot } returns mapOf(DefaultContour.CHANNEL_HASH to 1)

        useCase()

        coVerify(exactly = 1) { contourRepository.setSosMode(false) }
    }

    @Test
    fun `использует Неизвестный при пустом callsign`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser(""))
        every { channelSlotResolver.hashToSlot } returns mapOf(DefaultContour.CHANNEL_HASH to 1)

        var capturedText = ""
        coEvery { sendChatMessage.invoke(any()) } answers {
            capturedText = firstArg<SendChatMessageParams>().text
        }

        useCase()

        assertTrue(capturedText.contains("Неизвестный"))
    }

    @Test
    fun `порядок — стоп, сообщение, SOS off`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { channelSlotResolver.hashToSlot } returns mapOf(DefaultContour.CHANNEL_HASH to 1)

        useCase()

        coVerifyOrder {
            broadcast.stop()
            sendChatMessage.invoke(any())
            contourRepository.setSosMode(false)
        }
    }
}
