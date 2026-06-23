package ru.tcynik.klitch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.logger.Logger
import org.koin.android.ext.android.inject
import ru.tcynik.klitch.MainActivity
import ru.tcynik.klitch.R
import ru.tcynik.klitch.domain.gps.repository.GpsLifecycleController
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.track.model.TrackPoint
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.repository.TrackRecordingRepository
import ru.tcynik.klitch.presentation.util.requestIgnoreBatteryOptimizationIfNeeded

class GpsService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 201
        private const val CHANNEL_GPS_IDLE = "gps_service_idle"
        private const val CHANNEL_GPS_RECORDING = "gps_service_recording"
        private const val RECORDING_NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L

        fun createIntent(context: Context): Intent = Intent(context, GpsService::class.java)
    }

    private val gpsLifecycle: GpsLifecycleController by inject()
    private val gpsRepository: GpsRepository by inject()
    private val trackRecordingRepository: TrackRecordingRepository by inject()
    private val logger: Logger by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        logger.d("GPS", "GpsService.onCreate")
        ensureNotificationChannel()
        requestIgnoreBatteryOptimizationIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d("GPS", "GpsService.onStartCommand")
        if (!isRunning) {
            isRunning = true
            gpsLifecycle.start()
            startTrackRecordingObserver()
            startRecordingNotificationObserver()
        }
        return START_NOT_STICKY
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
            trackRecordingRepository.state
                .map { state ->
                    (state as? TrackRecordingState.Recording)
                        ?.takeIf { !it.isPaused }
                        ?.trackId
                }
                .distinctUntilChanged()
                .collectLatest { activeTrackId ->
                    if (activeTrackId == null) return@collectLatest
                    val state = trackRecordingRepository.state.value as? TrackRecordingState.Recording
                        ?: return@collectLatest
                    collectGpsForTrack(state)
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
                ru.tcynik.klitch.domain.track.util.haversineMeters(
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

    private fun startRecordingNotificationObserver() {
        logger.d("GPS", "startRecordingNotificationObserver launched")
        serviceScope.launch {
            trackRecordingRepository.state
                .map { it is TrackRecordingState.Recording }
                .distinctUntilChanged()
                .collectLatest { isRecording ->
                    logger.d("GPS", "RecordingNotificationObserver: isRecording=$isRecording")
                    if (!isRecording) {
                        updateForegroundNotification(buildNotification())
                        return@collectLatest
                    }
                    while (true) {
                        val state = trackRecordingRepository.state.value
                        logger.d("GPS", "RecordingNotificationObserver: state=${state::class.simpleName}")
                        val recording = state as? TrackRecordingState.Recording ?: break
                        logger.d("GPS", "RecordingNotificationObserver: updating notification, isPaused=${recording.isPaused}, name=${recording.name}")
                        updateForegroundNotification(buildRecordingNotification(recording))
                        logger.d("GPS", "RecordingNotificationObserver: startForeground called, sleeping ${RECORDING_NOTIFICATION_UPDATE_INTERVAL_MS}ms")
                        delay(RECORDING_NOTIFICATION_UPDATE_INTERVAL_MS)
                    }
                    logger.d("GPS", "RecordingNotificationObserver: while loop exited (state no longer Recording)")
                }
        }
    }

    private fun updateForegroundNotification(notification: Notification) {
        Handler(Looper.getMainLooper()).post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            logger.d("GPS", "updateForegroundNotification: posted to main thread OK")
        }
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildRecordingNotification(state: TrackRecordingState.Recording): Notification {
        val nowSeconds = System.currentTimeMillis() / 1000
        val totalSeconds = state.accumulatedSeconds +
                if (!state.isPaused) (nowSeconds - state.activeFromSeconds) else 0L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val durationLabel = if (hours > 0) getString(R.string.gps_notification_duration_hm, hours, minutes)
        else getString(R.string.gps_notification_duration_m, minutes)
        val distanceKm = state.distanceMeters / 1000.0
        val distanceLabel = if (distanceKm >= 1.0) getString(R.string.gps_notification_distance_km, distanceKm)
        else getString(R.string.gps_notification_distance_m, state.distanceMeters.toInt())
        val statusLabel = if (state.isPaused)
            getString(R.string.track_recording_notification_paused)
        else
            getString(R.string.track_recording_notification_active)

        return NotificationCompat.Builder(this, CHANNEL_GPS_RECORDING)
            .setSmallIcon(R.drawable.ic_track_record)
            .setContentTitle("$statusLabel ${state.name}")
            .setContentText("$durationLabel • $distanceLabel")
            .setContentIntent(mainActivityIntent())
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GPS_IDLE,
                    getString(R.string.gps_notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GPS_RECORDING,
                    getString(R.string.track_recording_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_GPS_IDLE)
            .setSmallIcon(R.drawable.ic_triangle_arrow)
            .setContentTitle(getString(R.string.gps_notification_title))
            .setContentText(getString(R.string.gps_notification_text))
            .setContentIntent(mainActivityIntent())
            .setOngoing(true)
            .build()
}
