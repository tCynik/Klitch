package ru.tcynik.meshtactics.domain.location.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.meshtactics.domain.location.model.GpsRawStatus
import ru.tcynik.meshtactics.domain.location.model.GpsSignalLevel
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.location.repository.GpsStatusRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveGpsStatusUseCaseTest {

    private val repository: GpsStatusRepository = mockk()
    private val useCase = ObserveGpsStatusUseCase(repository)

    private fun givenRaw(satellites: Int, accuracy: Float?) {
        every { repository.observeRaw() } returns flowOf(
            GpsRawStatus(satelliteCount = satellites, accuracyMeters = accuracy)
        )
    }

    private suspend fun collectSignalLevel(): GpsSignalLevel {
        var level: GpsSignalLevel = GpsSignalLevel.None
        useCase(NoParams).test { level = awaitItem().signalLevel; awaitComplete() }
        return level
    }

    // ── None ────────────────────────────────────────────────────────────────

    @Test
    fun `accuracy null → None`() = runTest {
        givenRaw(satellites = 6, accuracy = null)
        assertEquals(GpsSignalLevel.None, collectSignalLevel())
    }

    @Test
    fun `zero satellites → None`() = runTest {
        givenRaw(satellites = 0, accuracy = 10f)
        assertEquals(GpsSignalLevel.None, collectSignalLevel())
    }

    // ── Weak ────────────────────────────────────────────────────────────────

    @Test
    fun `satellites below threshold → Weak`() = runTest {
        givenRaw(satellites = 3, accuracy = 10f)
        assertEquals(GpsSignalLevel.Weak, collectSignalLevel())
    }

    @Test
    fun `accuracy above threshold → Weak`() = runTest {
        givenRaw(satellites = 5, accuracy = 51f)
        assertEquals(GpsSignalLevel.Weak, collectSignalLevel())
    }

    @Test
    fun `exactly on accuracy boundary (above) → Weak`() = runTest {
        givenRaw(satellites = 4, accuracy = 50.1f)
        assertEquals(GpsSignalLevel.Weak, collectSignalLevel())
    }

    // ── Strong ───────────────────────────────────────────────────────────────

    @Test
    fun `strong signal — minimum boundary values`() = runTest {
        givenRaw(satellites = 4, accuracy = 50f)
        assertEquals(GpsSignalLevel.Strong, collectSignalLevel())
    }

    @Test
    fun `strong signal — typical good fix`() = runTest {
        givenRaw(satellites = 8, accuracy = 5f)
        assertEquals(GpsSignalLevel.Strong, collectSignalLevel())
    }

    // ── Model fields pass through ────────────────────────────────────────────

    @Test
    fun `satelliteCount and accuracyMeters pass through unchanged`() = runTest {
        givenRaw(satellites = 5, accuracy = 12.5f)
        val expected = GpsStatusModel(
            satelliteCount = 5,
            accuracyMeters = 12.5f,
            signalLevel = GpsSignalLevel.Strong,
        )
        useCase(NoParams).test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }
}
