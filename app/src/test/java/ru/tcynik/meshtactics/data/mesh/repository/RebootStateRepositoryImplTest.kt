package ru.tcynik.meshtactics.data.mesh.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebootStateRepositoryImplTest {

    private val repository = RebootStateRepositoryImpl()

    @Test
    fun `markSyncAppliedBeforeReboot — shouldSkipSyncCheckAfterReboot true до clear`() {
        repository.markSyncAppliedBeforeReboot()

        assertTrue(repository.shouldSkipSyncCheckAfterReboot())

        repository.clearSkipSyncCheckAfterReboot()

        assertFalse(repository.shouldSkipSyncCheckAfterReboot())
    }
}
