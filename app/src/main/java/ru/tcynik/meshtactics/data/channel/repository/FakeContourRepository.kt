package ru.tcynik.meshtactics.data.channel.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository

class FakeContourRepository : ContourRepository {

    private val _contours = MutableStateFlow<List<Contour>>(emptyList())
    private val _emergencyIsActive = MutableStateFlow(DefaultContour.IS_ACTIVE_DEFAULT)

    override fun observeContours(): Flow<List<Contour>> = _contours.asStateFlow()

    override fun observeEmergencyIsActive(): Flow<Boolean> = _emergencyIsActive.asStateFlow()

    override suspend fun setEmergencyActive(isActive: Boolean) {
        _emergencyIsActive.value = isActive
        _contours.update { current ->
            current.map { if (it.id == DefaultContour.ID) it.copy(isActive = isActive) else it }
        }
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
}
