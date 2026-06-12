package ru.tcynik.klitch.domain.mesh.repository

import kotlinx.coroutines.flow.Flow

interface GpsBroadcastSettingsRepository {
    val enabled: Flow<Boolean>
    suspend fun set(value: Boolean)
}
