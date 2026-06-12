package ru.tcynik.klitch.data.mesh

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.domain.channel.repository.ContourRepository

class GeoSendPolicyImplTest {

    private val contourRepository: ContourRepository = mockk()
    private val policy = GeoSendPolicyImpl(contourRepository)

    @Test
    fun `observeAllowed emits true when SOS inactive`() = runTest {
        every { contourRepository.observeSosMode() } returns flowOf(false)

        policy.observeAllowed().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAllowed emits false when SOS active`() = runTest {
        every { contourRepository.observeSosMode() } returns flowOf(true)

        policy.observeAllowed().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
