package ru.tcynik.meshtactics.data.mesh

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoSendPolicyImplTest {

    private val policy = GeoSendPolicyImpl()

    @Test
    fun `observeAllowed always emits true`() = runTest {
        policy.observeAllowed().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
