package ru.tcynik.meshtactics.domain.marker.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel

interface GeoMarkRepository {
    fun observeGeoMarks(): Flow<List<GeoMarkModel>>
    suspend fun sendGeoMark(mark: GeoMarkModel, contourId: ContourId? = null, localOnly: Boolean = false)
    suspend fun persistReceived(mark: GeoMarkModel, contourId: ContourId)
    suspend fun toggleVisibility(id: String, visible: Boolean)
    suspend fun updateExpiresAt(id: String, expiresAt: Long)
    suspend fun deleteById(id: String)
    suspend fun deleteExpired(nowSeconds: Long)
}
