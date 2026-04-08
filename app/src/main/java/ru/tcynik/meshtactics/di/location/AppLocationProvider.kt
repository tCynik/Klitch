package ru.tcynik.meshtactics.di.location

import android.annotation.SuppressLint
import android.app.Application
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

/**
 * OS-level [LocationProvider] for the app layer. Registers its own [LocationManagerCompat]
 * request at [UPDATE_INTERVAL_MS] (5 s), independently of the Mesh broadcast interval.
 *
 * [ru.tcynik.meshtactics.mesh.data.repository.LocationRepositoryImpl] and the Mesh
 * position broadcast are unaffected.
 */
class AppLocationProvider(
    private val context: Application,
) : LocationProvider {

    companion object {
        private const val UPDATE_INTERVAL_MS = 5_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("MissingPermission")
    override val location: StateFlow<Location?> = callbackFlow {
        val locationManager = context.getSystemService(LocationManager::class.java)

        val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        val listener = LocationListenerCompat { androidLocation ->
            val ageNanos = SystemClock.elapsedRealtimeNanos() - androidLocation.elapsedRealtimeNanos
            val mapped = Location(
                position = Position(
                    longitude = androidLocation.longitude,
                    latitude = androidLocation.latitude,
                ),
                accuracy = androidLocation.accuracy.toDouble(),
                bearing = if (androidLocation.hasBearing()) androidLocation.bearing.toDouble() else null,
                bearingAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && androidLocation.hasBearingAccuracy())
                    androidLocation.bearingAccuracyDegrees.toDouble()
                else null,
                speed = if (androidLocation.hasSpeed()) androidLocation.speed.toDouble() else null,
                speedAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && androidLocation.hasSpeedAccuracy())
                    androidLocation.speedAccuracyMetersPerSecond.toDouble()
                else null,
                timestamp = TimeSource.Monotonic.markNow() - ageNanos.nanoseconds,
            )
            trySend(mapped)
        }

        val providers = buildList {
            val all = locationManager.allProviders
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
                locationManager,
                provider,
                request,
                Dispatchers.IO.asExecutor(),
                listener,
            )
        }

        awaitClose {
            LocationManagerCompat.removeUpdates(locationManager, listener)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = null,
    )
}
