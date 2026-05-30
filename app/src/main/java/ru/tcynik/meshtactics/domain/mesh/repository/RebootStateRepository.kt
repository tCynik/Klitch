package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.StateFlow

interface RebootStateRepository {
    val isRebooting: StateFlow<Boolean>
    fun setRebooting(value: Boolean)
    /** Контуры записаны, reboot ожидается — пропустить checkNodeSync на следующем connect. */
    fun markSyncAppliedBeforeReboot()
    fun shouldSkipSyncCheckAfterReboot(): Boolean
    fun clearSkipSyncCheckAfterReboot()
}
