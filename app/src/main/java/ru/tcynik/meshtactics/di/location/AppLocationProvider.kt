package ru.tcynik.meshtactics.di.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

/**
 * Тонкий адаптер: читает [GpsRepository.location] и преобразует доменный [ru.tcynik.meshtactics.domain.gps.model.GpsLocation]
 * в MapLibre [Location]. Не подписывается на OS LocationManager напрямую.
 *
 * GPS-обновления поступают из [ru.tcynik.meshtactics.data.gps.GpsRepositoryImpl],
 * которым управляет [ru.tcynik.meshtactics.service.GpsService].
 */
class AppLocationProvider(
    private val gpsRepository: GpsRepository,
) : LocationProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val location: StateFlow<Location?> = gpsRepository.location
        .map { gpsLocation ->
            gpsLocation?.let {
                val ageNanos = SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos
                Location(
                    position = Position(
                        longitude = it.longitude,
                        latitude = it.latitude,
                    ),
                    accuracy = it.accuracy.toDouble(),
                    bearing = it.bearing?.toDouble(),
                    bearingAccuracy = null,
                    speed = it.speed?.toDouble(),
                    speedAccuracy = null,
                    timestamp = TimeSource.Monotonic.markNow() - ageNanos.nanoseconds,
                )
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
