package ru.tcynik.klitch.data.gps

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.mesh.repository.LocationRepository

class MeshLocationRepositoryAdapter(
    private val gpsRepository: GpsRepository,
) : LocationRepository {

    override val receivingLocationUpdates: StateFlow<Boolean> =
        gpsRepository.isReceivingUpdates

    override fun getLocations(): Flow<Location> =
        gpsRepository.location
            .filterNotNull()
            .map { gpsLocation ->
                Location("gps-service").apply {
                    latitude = gpsLocation.latitude
                    longitude = gpsLocation.longitude
                    accuracy = gpsLocation.accuracy
                    gpsLocation.bearing?.let { bearing = it }
                    gpsLocation.speed?.let { speed = it }
                    elapsedRealtimeNanos = gpsLocation.elapsedRealtimeNanos
                    time = gpsLocation.time
                }
            }
}
