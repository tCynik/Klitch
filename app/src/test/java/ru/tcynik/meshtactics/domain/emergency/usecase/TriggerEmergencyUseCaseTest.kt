package ru.tcynik.meshtactics.domain.emergency.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.meshtactics.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.gps.model.GpsLocation
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.repository.AppUserRepository

class TriggerEmergencyUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val setPrimaryContour: SetPrimaryContourUseCase = mockk(relaxed = true)
    private val appUserRepository: AppUserRepository = mockk()
    private val gpsRepository: GpsRepository = mockk()
    private val sendChatMessage: SendChatMessageUseCase = mockk(relaxed = true)
    private val broadcast: EmergencyPositionBroadcastRepository = mockk(relaxed = true)

    private val useCase = TriggerEmergencyUseCase(
        contourRepository = contourRepository,
        setPrimaryContour = setPrimaryContour,
        appUserRepository = appUserRepository,
        gpsRepository = gpsRepository,
        sendChatMessage = sendChatMessage,
        broadcast = broadcast,
    )

    @Before
    fun setUp() {
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultActiveContour.ID
    }

    @Test
    fun `сохраняет preSosPrimary, включает SOS и назначает Emergency Primary`() = runTest {
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultActiveContour.ID
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { gpsRepository.location } returns MutableStateFlow(null)

        useCase()

        coVerify(exactly = 1) { contourRepository.savePreSosPrimaryId(DefaultActiveContour.ID) }
        coVerify(exactly = 1) { contourRepository.setSosMode(true) }
        coVerify(exactly = 1) { setPrimaryContour(DefaultContour.ID) }
    }

    @Test
    fun `отправляет сообщение с координатами когда GPS доступен`() = runTest {
        val location = GpsLocation(55.75, 37.62, null, null, 10f, 0L)
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { gpsRepository.location } returns MutableStateFlow(location)

        var capturedParams: SendChatMessageParams? = null
        coEvery { sendChatMessage.invoke(any()) } answers { capturedParams = firstArg() }

        useCase()

        assertTrue(capturedParams!!.text.contains("Иван просит помощи"))
        assertTrue(capturedParams!!.text.contains("55.75"))
        assertTrue(capturedParams!!.text.contains("37.62"))
        assertEquals("^all", capturedParams!!.contactId)
        assertEquals(0, capturedParams!!.channel)
    }

    @Test
    fun `отправляет сообщение без координат когда GPS недоступен`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { gpsRepository.location } returns MutableStateFlow(null)

        var capturedText = ""
        coEvery { sendChatMessage.invoke(any()) } answers {
            capturedText = firstArg<SendChatMessageParams>().text
        }

        useCase()

        assertTrue(capturedText.contains("Иван просит помощи"))
        assertFalse(capturedText.contains("координаты"))
    }

    @Test
    fun `использует Неизвестный при пустом callsign`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser(""))
        every { gpsRepository.location } returns MutableStateFlow(null)

        var capturedText = ""
        coEvery { sendChatMessage.invoke(any()) } answers {
            capturedText = firstArg<SendChatMessageParams>().text
        }

        useCase()

        assertTrue(capturedText.contains("Неизвестный"))
    }

    @Test
    fun `запускает трансляцию геопозиции`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { gpsRepository.location } returns MutableStateFlow(null)

        useCase()

        verify(exactly = 1) { broadcast.start() }
    }

    @Test
    fun `порядок — preSos, SOS, Primary, сообщение, трансляция`() = runTest {
        every { appUserRepository.observeUser() } returns flowOf(AppUser("Иван"))
        every { gpsRepository.location } returns MutableStateFlow(null)

        useCase()

        coVerifyOrder {
            contourRepository.savePreSosPrimaryId(any())
            contourRepository.setSosMode(true)
            setPrimaryContour(DefaultContour.ID)
            sendChatMessage.invoke(any())
            broadcast.start()
        }
    }
}
