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
import android.util.Log
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.inject
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController

class GpsService : Service() {

    companion object {
        private const val TAG = "GpsService"
        private const val NOTIFICATION_ID = 201
        private const val CHANNEL_ID = "gps_service_channel"

        // TODO (track-recording plan): startTrackRecording(scope) — корутина в service scope,
        //   подписывается на GpsRepository, буферизует точки в SQLDelight
        fun createIntent(context: Context): Intent = Intent(context, GpsService::class.java)
    }

    private val gpsLifecycle: GpsLifecycleController by inject()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        ensureNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        gpsLifecycle.start()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved → stopSelf")
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        gpsLifecycle.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
