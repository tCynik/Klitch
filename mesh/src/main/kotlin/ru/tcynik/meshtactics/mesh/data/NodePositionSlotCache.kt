package ru.tcynik.meshtactics.mesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
class NodePositionSlotCache {
    private val _slots = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val slots: StateFlow<Map<Int, Int>> = _slots.asStateFlow()

    fun record(nodeNum: Int, slot: Int) {
        _slots.update { it + (nodeNum to slot) }
    }
}
