package ru.tcynik.meshtactics.domain.emergency.repository

import kotlinx.coroutines.flow.StateFlow

interface EmergencyPositionBroadcastRepository {
    val isActive: StateFlow<Boolean>
    fun start()
    fun stop()
}
