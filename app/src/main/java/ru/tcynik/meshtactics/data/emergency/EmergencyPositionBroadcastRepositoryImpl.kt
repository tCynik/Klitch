package ru.tcynik.meshtactics.data.emergency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.meshtactics.domain.gps.model.GpsLocation
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import java.util.UUID

private const val BROADCAST_INTERVAL_MS = 30_000L

class EmergencyPositionBroadcastRepositoryImpl(
    private val gpsRepository: GpsRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val contourRepository: ContourRepository,
) : EmergencyPositionBroadcastRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    init {
        scope.launch {
            val wasActive = contourRepository.observeEmergencyIsActive().first()
            if (wasActive) start()
        }
    }

    override fun start() {
        if (broadcastJob?.isActive == true) return
        _isActive.value = true
        broadcastJob = scope.launch {
            while (true) {
                gpsRepository.location.value?.let { sendPositionMark(it) }
                delay(BROADCAST_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null
        _isActive.value = false
    }

    private suspend fun sendPositionMark(location: GpsLocation) {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val markId = UUID.randomUUID().toString()
        geoMarkRepository.sendGeoMark(
            GeoMarkModel(
                id = markId,
                waypointId = GeoMarkWaypointAdapter.waypointIdFromMarkId(markId),
                type = GeoMarkType.POINT,
                points = listOf(GeoPoint(location.latitude, location.longitude)),
                authorNodeId = "",
                createdAt = nowSeconds,
                expiresAt = null,
                isSelf = true,
            )
        )
    }
}
