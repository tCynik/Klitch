package ru.tcynik.meshtactics.data.track.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.data.local.RecordedTrackPointQueries
import ru.tcynik.meshtactics.data.local.RecordedTrackQueries
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.track.model.RecordedTrack
import ru.tcynik.meshtactics.domain.track.model.TrackPoint
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingSettings
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.domain.track.repository.RecordedTrackRepository
import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository
import ru.tcynik.meshtactics.domain.track.util.haversineMeters
import java.util.UUID

class TrackRepositoryImpl(
    private val trackQueries: RecordedTrackQueries,
    private val pointQueries: RecordedTrackPointQueries,
    private val logger: Logger,
) : RecordedTrackRepository, TrackRecordingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<TrackRecordingState>(TrackRecordingState.Idle)
    override val state: StateFlow<TrackRecordingState> = _state.asStateFlow()

    private var lastRecordedPoint: TrackPoint? = null

    init {
        scope.launch {
            val unfinished = trackQueries.selectUnfinished().executeAsOneOrNull()
            if (unfinished != null) {
                val points = pointQueries.selectByTrackId(unfinished.id).executeAsList()
                lastRecordedPoint = points.lastOrNull()?.let { row ->
                    TrackPoint(
                        trackId = row.track_id,
                        timestampMs = row.timestamp,
                        lat = row.lat,
                        lon = row.lon,
                        accuracy = row.accuracy.toFloat(),
                    )
                }
                _state.value = TrackRecordingState.Recording(
                    trackId = unfinished.id,
                    name = unfinished.name,
                    startedAtSeconds = unfinished.started_at,
                    settings = TrackRecordingSettings(),
                    distanceMeters = 0.0,
                    pointCount = points.size,
                )
                logger.d(TAG, "Restored unfinished track ${unfinished.id} (${points.size} points)")
            }
        }
    }

    // ── RecordedTrackRepository ──────────────────────────────────────────────

    override fun observeTracks(): Flow<List<RecordedTrack>> =
        trackQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun setVisible(id: String, visible: Boolean) {
        trackQueries.setVisible(isVisible = if (visible) 1L else 0L, id = id)
    }

    override suspend fun deleteById(id: String) {
        pointQueries.deleteByTrackId(id)
        trackQueries.deleteById(id)
    }

    // ── TrackRecordingRepository ─────────────────────────────────────────────

    override suspend fun start(settings: TrackRecordingSettings) {
        val nowSeconds = System.currentTimeMillis() / 1_000
        val id = UUID.randomUUID().toString()
        val fullName = settings.name + (settings.nameCounter?.let { " $it" } ?: "")
        trackQueries.insert(
            id = id,
            name = fullName,
            startedAt = nowSeconds,
            color = settings.color.toLong(),
        )
        lastRecordedPoint = null
        _state.value = TrackRecordingState.Recording(
            trackId = id,
            name = fullName,
            startedAtSeconds = nowSeconds,
            settings = settings,
        )
        logger.d(TAG, "Started track $id: $fullName")
    }

    override suspend fun addPoint(point: TrackPoint) {
        pointQueries.insertPoint(
            trackId = point.trackId,
            timestamp = point.timestampMs,
            lat = point.lat,
            lon = point.lon,
            accuracy = point.accuracy.toDouble(),
        )
        val current = _state.value as? TrackRecordingState.Recording ?: return
        val delta = lastRecordedPoint?.let { prev ->
            haversineMeters(prev.lat, prev.lon, point.lat, point.lon)
        } ?: 0.0
        lastRecordedPoint = point
        _state.value = current.copy(
            distanceMeters = current.distanceMeters + delta,
            pointCount = current.pointCount + 1,
        )
    }

    override suspend fun pause() {
        val current = _state.value as? TrackRecordingState.Recording ?: return
        if (!current.isPaused) {
            _state.value = current.copy(isPaused = true)
            logger.d(TAG, "Paused track ${current.trackId}")
        }
    }

    override suspend fun resume() {
        val current = _state.value as? TrackRecordingState.Recording ?: return
        if (current.isPaused) {
            lastRecordedPoint = null
            _state.value = current.copy(isPaused = false)
            logger.d(TAG, "Resumed track ${current.trackId}")
        }
    }

    override suspend fun stop(name: String?) {
        val current = _state.value as? TrackRecordingState.Recording ?: return
        val finalName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (finalName != null && finalName != current.name) {
            trackQueries.updateName(name = finalName, id = current.trackId)
        }
        val nowSeconds = System.currentTimeMillis() / 1_000
        val totalDistance = computeTotalDistance(current.trackId)
        trackQueries.updateFinished(
            finishedAt = nowSeconds,
            totalDistance = totalDistance,
            id = current.trackId,
        )
        lastRecordedPoint = null
        _state.value = TrackRecordingState.Idle
        logger.d(TAG, "Stopped track ${current.trackId}, distance=${totalDistance}m")
    }

    override suspend fun discard() {
        val current = _state.value as? TrackRecordingState.Recording ?: return
        pointQueries.deleteByTrackId(current.trackId)
        trackQueries.deleteById(current.trackId)
        lastRecordedPoint = null
        _state.value = TrackRecordingState.Idle
        logger.d(TAG, "Discarded track ${current.trackId}")
    }

    private fun computeTotalDistance(trackId: String): Double {
        val points = pointQueries.selectByTrackId(trackId).executeAsList()
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(
                lat1 = points[i - 1].lat, lon1 = points[i - 1].lon,
                lat2 = points[i].lat,     lon2 = points[i].lon,
            )
        }
        return total
    }

    private fun ru.tcynik.meshtactics.data.local.Recorded_track.toModel() = RecordedTrack(
        id = id,
        name = name,
        startedAt = started_at,
        finishedAt = finished_at,
        totalDistanceMeters = total_distance,
        color = color.toInt(),
        isVisible = is_visible == 1L,
    )

    companion object {
        private const val TAG = "Track"
    }
}
