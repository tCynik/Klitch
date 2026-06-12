package ru.tcynik.klitch.domain.settings.repository

import kotlinx.coroutines.flow.StateFlow

interface NetworkSettingsRepository {
    val networkEnabledFlow: StateFlow<Boolean>
    fun getNetworkEnabled(): Boolean
    fun setNetworkEnabled(enabled: Boolean)
}
