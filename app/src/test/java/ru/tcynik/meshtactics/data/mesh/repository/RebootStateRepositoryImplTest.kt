package ru.tcynik.meshtactics.data.mesh.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.meshtactics.logger.NoOpLogger
class RebootStateRepositoryImplTest {

    private val repository = RebootStateRepositoryImpl(NoOpLogger())

    @Test
    fun `markSyncAppliedBeforeReboot — shouldSkipSyncCheckAfterReboot true до clear`() {
        repository.markSyncAppliedBeforeReboot()

        assertTrue(repository.shouldSkipSyncCheckAfterReboot())

        repository.clearSkipSyncCheckAfterReboot()

        assertFalse(repository.shouldSkipSyncCheckAfterReboot())
    }

    @Test
    fun `setSyncCyclePhase — isRebooting синхронизирован с фазой`() {
        repository.setSyncCyclePhase(NodeSyncCyclePhase.Syncing)
        assertTrue(repository.isRebooting.value)
        assertTrue(repository.syncCyclePhase.value == NodeSyncCyclePhase.Syncing)

        repository.setSyncCyclePhase(NodeSyncCyclePhase.Idle)
        assertFalse(repository.isRebooting.value)
    }
}
