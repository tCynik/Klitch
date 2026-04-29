package ru.tcynik.meshtactics.data.mesh.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository

class RebootStateRepositoryImpl : RebootStateRepository {
    private val _isRebooting = MutableStateFlow(false)
    override val isRebooting: StateFlow<Boolean> = _isRebooting.asStateFlow()
    override fun setRebooting(value: Boolean) { _isRebooting.value = value }
}
