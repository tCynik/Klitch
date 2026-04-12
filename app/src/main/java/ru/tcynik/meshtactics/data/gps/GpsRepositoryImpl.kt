package ru.tcynik.meshtactics.data.gps

import android.annotation.SuppressLint
import android.app.Application
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.meshtactics.domain.gps.model.GpsLocation
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository

class GpsRepositoryImpl(
    private val context: Application,
) : GpsRepository, GpsLifecycleController {

    companion object {
        private const val UPDATE_INTERVAL_MS = 5_000L
    }

    private val _location = MutableStateFlow<GpsLocation?>(null)
    override val location: StateFlow<GpsLocation?> = _location.asStateFlow()

    private val _isReceivingUpdates = MutableStateFlow(false)
    override val isReceivingUpdates: StateFlow<Boolean> = _isReceivingUpdates.asStateFlow()

    @Volatile private var activeLocationManager: LocationManager? = null
    @Volatile private var activeListener: LocationListenerCompat? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        Logger.i {"start gps repo"}
        if (_isReceivingUpdates.value) return

        val lm = context.getSystemService(LocationManager::class.java)

        val listener = LocationListenerCompat { androidLocation ->
            _location.value = GpsLocation(
                latitude = androidLocation.latitude,
                longitude = androidLocation.longitude,
                bearing = if (androidLocation.hasBearing()) androidLocation.bearing else null,
                speed = if (androidLocation.hasSpeed()) androidLocation.speed else null,
                accuracy = androidLocation.accuracy,
                elapsedRealtimeNanos = androidLocation.elapsedRealtimeNanos,
            )
        }

        val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        val providers = buildList {
            val all = lm.allProviders
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in all
            ) {
                add(LocationManager.FUSED_PROVIDER)
            } else {
                if (LocationManager.GPS_PROVIDER in all) add(LocationManager.GPS_PROVIDER)
                if (LocationManager.NETWORK_PROVIDER in all) add(LocationManager.NETWORK_PROVIDER)
            }
        }

        providers.forEach { provider ->
            LocationManagerCompat.requestLocationUpdates(
                lm,
                provider,
                request,
                Dispatchers.IO.asExecutor(),
                listener,
            )
        }

        activeLocationManager = lm
        activeListener = listener
        _isReceivingUpdates.value = true
    }

    override fun stop() {
        Logger.i {"stop gps repo"}
        val lm = activeLocationManager ?: return
        val listener = activeListener ?: return
        LocationManagerCompat.removeUpdates(lm, listener)
        activeLocationManager = null
        activeListener = null
        _isReceivingUpdates.value = false
    }
}
