package ru.tcynik.meshtactics.data.mesh

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository

class GeoSendPolicyImplTest {

    private val repository: ContourRepository = mockk()
    private val sosModeFlow = MutableStateFlow(false)

    private val policy = GeoSendPolicyImpl(repository).also {
        every { repository.observeSosMode() } returns sosModeFlow
    }

    @Test
    fun `SOS active — observeAllowed emits false`() = runTest {
        sosModeFlow.value = true

        policy.observeAllowed().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SOS inactive — observeAllowed emits true`() = runTest {
        sosModeFlow.value = false

        policy.observeAllowed().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flow updates when SOS status changes`() = runTest {
        sosModeFlow.value = false

        policy.observeAllowed().test {
            assertEquals(true, awaitItem())

            sosModeFlow.value = true
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
