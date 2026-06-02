package ru.tcynik.meshtactics.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.logger.Logger
import org.koin.android.ext.android.inject
import ru.tcynik.meshtactics.MainActivity
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.track.model.TrackPoint
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.domain.track.repository.TrackRecordingRepository

class GpsService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 201
        private const val REMINDER_NOTIFICATION_ID = 202
        private const val CHANNEL_ID = "gps_service_channel"
        private const val REMINDER_CHANNEL_ID = "track_reminder"
        private const val RECORDING_REMINDER_INTERVAL_MS = 2 * 60 * 60 * 1000L

        fun createIntent(context: Context): Intent = Intent(context, GpsService::class.java)
    }

    private val gpsLifecycle: GpsLifecycleController by inject()
    private val gpsRepository: GpsRepository by inject()
    private val trackRecordingRepository: TrackRecordingRepository by inject()
    private val logger: Logger by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        startReminderObserver()
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

    private fun startReminderObserver() {
        serviceScope.launch {
            trackRecordingRepository.state
                .map { it is TrackRecordingState.Recording }
                .distinctUntilChanged()
                .collectLatest { isRecording ->
                    if (!isRecording) {
                        NotificationManagerCompat.from(this@GpsService)
                            .cancel(REMINDER_NOTIFICATION_ID)
                        return@collectLatest
                    }
                    while (true) {
                        delay(RECORDING_REMINDER_INTERVAL_MS)
                        val state = trackRecordingRepository.state.value
                            as? TrackRecordingState.Recording ?: break
                        val isForegrounded = ProcessLifecycleOwner.get().lifecycle
                            .currentState.isAtLeast(Lifecycle.State.RESUMED)
                        if (isForegrounded) continue
                        showReminderNotification(state)
                    }
                }
        }
    }

    private fun showReminderNotification(state: TrackRecordingState.Recording) {
        val nowSeconds = System.currentTimeMillis() / 1000
        val totalSeconds = state.accumulatedSeconds +
                if (!state.isPaused) (nowSeconds - state.activeFromSeconds) else 0L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val durationLabel = if (hours > 0) "${hours}ч ${minutes}мин" else "${minutes} мин"
        val distanceKm = state.distanceMeters / 1000.0
        val distanceLabel = if (distanceKm >= 1.0) "%.2f км".format(distanceKm)
        else "${state.distanceMeters.toInt()} м"

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_triangle_arrow)
            .setContentTitle(getString(R.string.track_reminder_notification_title))
            .setContentText("${state.name} • $durationLabel • $distanceLabel")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            logger.w("Track", "POST_NOTIFICATIONS not granted — reminder skipped")
            return
        }
        NotificationManagerCompat.from(this).notify(REMINDER_NOTIFICATION_ID, notification)
        logger.i("Track", "Reminder notification sent for track ${state.trackId}")
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.gps_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    getString(R.string.track_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
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
