package ru.tcynik.klitch.domain.channel.repository

import kotlinx.coroutines.flow.StateFlow

interface ContourSyncStateRepository {
    val syncRequired: StateFlow<Boolean>
    fun setSyncRequired(value: Boolean)
    fun clear()
}
