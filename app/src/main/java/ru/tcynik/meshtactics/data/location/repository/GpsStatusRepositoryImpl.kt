package ru.tcynik.meshtactics.data.location.repository

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.tcynik.meshtactics.domain.location.model.GpsRawStatus
import ru.tcynik.meshtactics.domain.location.repository.GpsStatusRepository

private const val LOCATION_UPDATE_INTERVAL_MS = 5_000L

/**
 * Combines two independent OS streams:
 * - [GnssStatus.Callback] — satellite count (usedInFix satellites only)
 * - [LocationListenerCompat] for GPS_PROVIDER — horizontal accuracy
 *
 * Independent of [AppLocationProvider]: both subscribe to LocationManager separately.
 * AppLocationProvider feeds MapLibre rendering; this repository feeds the domain GPS status pipeline.
 */
class GpsStatusRepositoryImpl(
    private val context: Application,
) : GpsStatusRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private val satelliteCountFlow = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(LocationManager::class.java)

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFix = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) usedInFix++
                }
                trySend(usedInFix)
            }

            override fun onStopped() {
                trySend(0)
            }
        }

        locationManager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))

        awaitClose {
            locationManager.unregisterGnssStatusCallback(callback)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), initialValue = 0)

    @SuppressLint("MissingPermission")
    private val accuracyFlow = callbackFlow<Float?> {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(LocationManager::class.java)

        val listener = LocationListenerCompat { location ->
            trySend(if (location.hasAccuracy()) location.accuracy else null)
        }

        val request = LocationRequestCompat.Builder(LOCATION_UPDATE_INTERVAL_MS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        if (LocationManager.GPS_PROVIDER in locationManager.allProviders) {
            LocationManagerCompat.requestLocationUpdates(
                locationManager,
                LocationManager.GPS_PROVIDER,
                request,
                Dispatchers.IO.asExecutor(),
                listener,
            )
        }

        awaitClose {
            LocationManagerCompat.removeUpdates(locationManager, listener)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), initialValue = null)

    override fun observeRaw(): Flow<GpsRawStatus> =
        combine(satelliteCountFlow, accuracyFlow) { satellites, accuracy ->
            GpsRawStatus(satelliteCount = satellites, accuracyMeters = accuracy)
        }
}
