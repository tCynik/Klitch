package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow

interface GpsBroadcastSettingsRepository {
    val enabled: Flow<Boolean>
    suspend fun set(value: Boolean)
}
