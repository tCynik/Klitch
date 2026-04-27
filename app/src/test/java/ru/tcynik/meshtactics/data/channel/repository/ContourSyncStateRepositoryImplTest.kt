package ru.tcynik.meshtactics.data.channel.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourSyncStateRepositoryImplTest {

    private val repository = ContourSyncStateRepositoryImpl()

    @Test
    fun `начальное значение syncRequired — false`() = runTest {
        assertFalse(repository.syncRequired.value)
    }

    @Test
    fun `setSyncRequired(true) — syncRequired становится true`() = runTest {
        repository.setSyncRequired(true)

        assertTrue(repository.syncRequired.value)
    }

    @Test
    fun `setSyncRequired(false) — syncRequired становится false`() = runTest {
        repository.setSyncRequired(true)
        repository.setSyncRequired(false)

        assertFalse(repository.syncRequired.value)
    }

    @Test
    fun `clear() сбрасывает syncRequired в false`() = runTest {
        repository.setSyncRequired(true)
        repository.clear()

        assertFalse(repository.syncRequired.value)
    }

    @Test
    fun `syncRequired flow эмитит обновления`() = runTest {
        repository.syncRequired.test {
            assertFalse(awaitItem())

            repository.setSyncRequired(true)
            assertTrue(awaitItem())

            repository.clear()
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
