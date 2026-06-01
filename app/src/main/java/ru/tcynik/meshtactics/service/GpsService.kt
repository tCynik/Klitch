package ru.tcynik.meshtactics.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.logger.Logger
import org.koin.android.ext.android.inject
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.track.model.TrackPoint
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class GpsService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 201
        private const val CHANNEL_ID = "gps_service_channel"

        fun createIntent(context: Context): Intent = Intent(context, GpsService::class.java)
    }

    private val gpsLifecycle: GpsLifecycleController by inject()
    private val gpsRepository: GpsRepository by inject()
    private val trackRecordingRepository: TrackRecordingRepository by inject()
    private val logger: Logger by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        logger.d("GPS", "GpsService.onCreate")
        ensureNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d("GPS", "GpsService.onStartCommand")
        gpsLifecycle.start()
        startTrackRecordingObserver()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logger.d("GPS", "GpsService.onTaskRemoved → stopSelf")
        stopSelf()
    }

    override fun onDestroy() {
        logger.d("GPS", "GpsService.onDestroy")
        gpsLifecycle.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTrackRecordingObserver() {
        serviceScope.launch {
            trackRecordingRepository.state.collectLatest { state ->
                collectionJob?.cancel()
                collectionJob = null
                if (state is TrackRecordingState.Recording) {
                    collectionJob = launch { collectGpsForTrack(state) }
                }
            }
        }
    }

    private suspend fun collectGpsForTrack(initialState: TrackRecordingState.Recording) {
        var lastTimestampMs = 0L
        var lastLat = Double.NaN
        var lastLon = Double.NaN
        val settings = initialState.settings

        gpsRepository.location.collectLatest { location ->
            location ?: return@collectLatest
            val nowMs = System.currentTimeMillis()
            val isFirst = lastTimestampMs == 0L

            val timeSinceLast = (nowMs - lastTimestampMs) / 1_000.0
            val distFromLast = if (lastLat.isNaN()) 0.0 else
                ru.tcynik.meshtactics.domain.track.util.haversineMeters(
                    lastLat, lastLon, location.latitude, location.longitude,
                )

            val timeTriggered = settings.intervalSeconds != null &&
                    timeSinceLast >= settings.intervalSeconds
            val distTriggered = settings.minDistanceMeters > 0 &&
                    distFromLast >= settings.minDistanceMeters

            if (isFirst || timeTriggered || distTriggered) {
                val currentState = trackRecordingRepository.state.value
                if (currentState !is TrackRecordingState.Recording) return@collectLatest
                if (currentState.isPaused) return@collectLatest
                trackRecordingRepository.addPoint(
                    TrackPoint(
                        trackId = currentState.trackId,
                        timestampMs = nowMs,
                        lat = location.latitude,
                        lon = location.longitude,
                        accuracy = location.accuracy,
                    )
                )
                lastTimestampMs = nowMs
                lastLat = location.latitude
                lastLon = location.longitude
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.gps_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_triangle_arrow)
            .setContentTitle(getString(R.string.gps_notification_title))
            .setContentText(getString(R.string.gps_notification_text))
            .setOngoing(true)
            .build()
}
