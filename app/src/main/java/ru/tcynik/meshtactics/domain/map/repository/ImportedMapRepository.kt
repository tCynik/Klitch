package ru.tcynik.meshtactics.domain.map.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.map.model.ImportedMapOverlay

interface ImportedMapRepository {
    fun observeAll(): Flow<List<ImportedMapOverlay>>
    suspend fun import(uri: String, name: String, createdAt: Long)
    suspend fun hide(id: String)
    suspend fun delete(id: String)
    suspend fun setSelected(id: String, selected: Boolean)
}
