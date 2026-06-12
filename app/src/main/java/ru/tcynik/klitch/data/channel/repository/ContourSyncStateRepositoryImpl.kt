package ru.tcynik.klitch.data.channel.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository

class ContourSyncStateRepositoryImpl : ContourSyncStateRepository {
    private val _syncRequired = MutableStateFlow(false)
    override val syncRequired: StateFlow<Boolean> = _syncRequired.asStateFlow()
    override fun setSyncRequired(value: Boolean) { _syncRequired.value = value }
    override fun clear() { _syncRequired.value = false }
}
