package ru.tcynik.meshtactics.data.mesh.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository

class RebootStateRepositoryImpl(
    private val logger: Logger,
) : RebootStateRepository {
    private val _isRebooting = MutableStateFlow(false)
    override val isRebooting: StateFlow<Boolean> = _isRebooting.asStateFlow()
    override fun setRebooting(value: Boolean) {
        if (value && !_isRebooting.value) {
            logger.i("Node", "rebootState: reboot cycle started")
        } else if (!value && _isRebooting.value) {
            logger.i("Node", "rebootState: reboot cycle finished")
        }
        _isRebooting.value = value
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
