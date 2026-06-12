package ru.tcynik.klitch.domain.map.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.map.model.ImportedMapOverlay

interface ImportedMapRepository {
    fun observeAll(): Flow<List<ImportedMapOverlay>>
    fun observeSelected(): Flow<List<ImportedMapOverlay>>
    suspend fun import(uri: String, name: String, createdAt: Long)
    suspend fun hide(id: String)
    suspend fun delete(id: String)
    suspend fun setSelected(id: String, selected: Boolean)
}
