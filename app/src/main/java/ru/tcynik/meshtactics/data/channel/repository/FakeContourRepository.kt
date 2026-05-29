package ru.tcynik.meshtactics.data.channel.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository

class FakeContourRepository : ContourRepository {

    private val _contours = MutableStateFlow<List<Contour>>(emptyList())
    private val _primaryContourId = MutableStateFlow(DefaultActiveContour.ID)
    private val _sosMode = MutableStateFlow(false)
    private var _preSosPrimaryId: ContourId? = null

    override fun observeContours(): Flow<List<Contour>> = _contours.asStateFlow()

    override fun observePrimaryContourId(): Flow<ContourId> = _primaryContourId.asStateFlow()

    override suspend fun getPrimaryContourId(): ContourId = _primaryContourId.value

    override suspend fun setPrimaryContour(id: ContourId) {
        _primaryContourId.value = id
    }

    override fun observeSosMode(): Flow<Boolean> = _sosMode.asStateFlow()

    override suspend fun setSosMode(active: Boolean) {
        _sosMode.value = active
    }

    override suspend fun getPreSosPrimaryId(): ContourId? = _preSosPrimaryId

    override suspend fun savePreSosPrimaryId(id: ContourId?) {
        _preSosPrimaryId = id
    }

    override suspend fun seedDefaultsIfAbsent() = Unit

    override suspend fun saveContour(contour: Contour) {
        _contours.update { current ->
            val existing = current.indexOfFirst { it.id == contour.id }
            if (existing >= 0) current.toMutableList().also { it[existing] = contour }
            else current + contour
        }
    }

    override suspend fun deleteContour(id: ContourId) {
        _contours.update { current -> current.filter { it.id != id } }
    }

    override suspend fun findByChannelHash(hash: ContourHash): Contour? =
        _contours.value.firstOrNull { it.transport.meshtastic.channelHash == hash }
            ?: if (hash == DefaultContour.CHANNEL_HASH) {
                DefaultContour.asContour().copy(isActive = true)
            } else {
                null
            }

    fun setContours(contours: List<Contour>) {
        _contours.value = contours
    }

    suspend fun currentContours(): List<Contour> = observeContours().first()
}
