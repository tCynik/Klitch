package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.StateFlow

interface RebootStateRepository {
    val isRebooting: StateFlow<Boolean>
    fun setRebooting(value: Boolean)
}
