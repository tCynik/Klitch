package ru.tcynik.meshtactics.domain.marker.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel

interface GeoMarkRepository {
    fun observeGeoMarks(): Flow<List<GeoMarkModel>>
    suspend fun sendGeoMark(mark: GeoMarkModel)
    suspend fun persistReceived(mark: GeoMarkModel, logicalChannelId: LogicalChannelId)
    suspend fun deleteExpired(nowSeconds: Long)
}
