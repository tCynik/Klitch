package ru.tcynik.klitch.data.emergency

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
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.emergency.repository.EmergencyPositionBroadcastRepository
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

private const val BROADCAST_INTERVAL_MS = 30_000L

class EmergencyPositionBroadcastRepositoryImpl(
    private val gpsRepository: GpsRepository,
    private val contourRepository: ContourRepository,
    private val meshConfigRepository: MeshConfigRepository,
) : EmergencyPositionBroadcastRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    init {
        scope.launch {
            val wasActive = contourRepository.observeSosMode().first()
            if (wasActive) start()
        }
    }

    override fun start() {
        if (broadcastJob?.isActive == true) return
        _isActive.value = true
        broadcastJob = scope.launch {
            while (true) {
                gpsRepository.location.value?.let { location ->
                    meshConfigRepository.setFixedPosition(location.latitude, location.longitude, 0)
                }
                delay(BROADCAST_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null
        _isActive.value = false
        meshConfigRepository.removeOwnFixedPosition()
    }
}
