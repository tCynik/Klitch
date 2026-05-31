package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.mesh.model.NodeSyncCyclePhase

interface RebootStateRepository {
    val isRebooting: StateFlow<Boolean>
    val syncCyclePhase: StateFlow<NodeSyncCyclePhase>
    fun setRebooting(value: Boolean)
    fun setSyncCyclePhase(phase: NodeSyncCyclePhase)
    /** Контуры записаны, reboot ожидается — пропустить checkNodeSync на следующем connect. */
    fun markSyncAppliedBeforeReboot()
    fun shouldSkipSyncCheckAfterReboot(): Boolean
    fun clearSkipSyncCheckAfterReboot()
}
