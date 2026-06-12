package ru.tcynik.meshtactics.domain.channel.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId

interface ContourRepository {
    fun observeContours(): Flow<List<Contour>>
    fun observePrimaryContourId(): Flow<ContourId>
    suspend fun getPrimaryContourId(): ContourId
    suspend fun setPrimaryContour(id: ContourId)
    fun observeSosMode(): Flow<Boolean>
    suspend fun setSosMode(active: Boolean)
    suspend fun getPreSosPrimaryId(): ContourId?
    suspend fun savePreSosPrimaryId(id: ContourId?)
    suspend fun seedDefaultsIfAbsent()
    suspend fun saveContour(contour: Contour)
    suspend fun deleteContour(id: ContourId)
    suspend fun findByChannelHash(hash: ContourHash): Contour?
}
