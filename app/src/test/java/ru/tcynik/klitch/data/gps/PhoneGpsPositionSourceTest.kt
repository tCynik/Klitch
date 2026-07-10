package ru.tcynik.klitch.data.gps

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.repository.GpsRepository

class PhoneGpsPositionSourceTest {

    private val gpsRepository: GpsRepository = mockk()
    private val locationFlow = MutableStateFlow<GpsLocation?>(null)

    private fun source(): PhoneGpsPositionSource {
        every { gpsRepository.location } returns locationFlow
        return PhoneGpsPositionSource(gpsRepository)
    }

    @Test
    fun `mode is PHONE_GPS`() {
        assertEquals(PositionSourceMode.PHONE_GPS, source().mode)
    }

    @Test
    fun `null location filtered out`() = runTest {
        source().observePosition().test {
            locationFlow.value = location(latitude = 55.0)
            assertEquals(55.0, awaitItem().latitude, 0.0)
        }
    }

    @Test
    fun `emits GpsRepository location updates as-is`() = runTest {
        val src = source()
        locationFlow.value = location(latitude = 10.0)

        src.observePosition().test {
            assertEquals(10.0, awaitItem().latitude, 0.0)
            locationFlow.value = location(latitude = 20.0)
            assertEquals(20.0, awaitItem().latitude, 0.0)
        }
    }

    private fun location(latitude: Double) = GpsLocation(
        latitude = latitude,
        longitude = 37.0,
        bearing = null,
        speed = null,
        accuracy = 5f,
        elapsedRealtimeNanos = 0L,
        time = 0L,
    )
}
