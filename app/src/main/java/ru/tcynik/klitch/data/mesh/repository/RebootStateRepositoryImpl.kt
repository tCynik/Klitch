package ru.tcynik.klitch.data.mesh.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository

class RebootStateRepositoryImpl(
    private val logger: Logger,
) : RebootStateRepository {
    private val _syncCyclePhase = MutableStateFlow(NodeSyncCyclePhase.Idle)
    override val syncCyclePhase: StateFlow<NodeSyncCyclePhase> = _syncCyclePhase.asStateFlow()

    private val _isRebooting = MutableStateFlow(false)
    override val isRebooting: StateFlow<Boolean> = _isRebooting.asStateFlow()

    override fun setSyncCyclePhase(phase: NodeSyncCyclePhase) {
        val previous = _syncCyclePhase.value
        if (phase == previous) return
        when {
            phase != NodeSyncCyclePhase.Idle && previous == NodeSyncCyclePhase.Idle ->
                logger.i("Node", "syncCycle: start -> $phase")
            phase == NodeSyncCyclePhase.Idle && previous != NodeSyncCyclePhase.Idle ->
                logger.i("Node", "syncCycle: $previous -> Idle (done)")
            else ->
                logger.i("Node", "syncCycle: $previous -> $phase")
        }
        _syncCyclePhase.value = phase
        _isRebooting.value = phase != NodeSyncCyclePhase.Idle
    }

    override fun setRebooting(value: Boolean) {
        if (value) {
            if (_syncCyclePhase.value == NodeSyncCyclePhase.Idle) {
                setSyncCyclePhase(NodeSyncCyclePhase.Rebooting)
            } else {
                _isRebooting.value = true
            }
        } else {
            setSyncCyclePhase(NodeSyncCyclePhase.Idle)
        }
    }

    private var skipSyncCheckAfterReboot = false

    override fun markSyncAppliedBeforeReboot() {
        skipSyncCheckAfterReboot = true
    }

    override fun shouldSkipSyncCheckAfterReboot(): Boolean = skipSyncCheckAfterReboot

    override fun clearSkipSyncCheckAfterReboot() {
        skipSyncCheckAfterReboot = false
    }
}
