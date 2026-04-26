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
    private val emergencyIsActiveFlow = MutableStateFlow(false)

    private val policy = GeoSendPolicyImpl(repository).also {
        every { repository.observeEmergencyIsActive() } returns emergencyIsActiveFlow
    }

    @Test
    fun `emergency isActive=true — observeAllowed emits false`() = runTest {
        emergencyIsActiveFlow.value = true

        policy.observeAllowed().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emergency isActive=false — observeAllowed emits true`() = runTest {
        emergencyIsActiveFlow.value = false

        policy.observeAllowed().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flow updates when emergency status changes`() = runTest {
        emergencyIsActiveFlow.value = false

        policy.observeAllowed().test {
            assertEquals(true, awaitItem())

            emergencyIsActiveFlow.value = true
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
