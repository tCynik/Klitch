package ru.tcynik.klitch.data.gps

import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

class GpsRepositoryImplTest {

    private val app: Application = mockk()
    private val locationManager: LocationManager = mockk(relaxed = true)
    private lateinit var repository: GpsRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(LocationManagerCompat::class)
        every { app.getSystemService(LocationManager::class.java) } returns locationManager
        every { locationManager.allProviders } returns listOf(LocationManager.GPS_PROVIDER)
        every {
            LocationManagerCompat.requestLocationUpdates(
                any(), any(), any<LocationRequestCompat>(), any<Executor>(), any<LocationListenerCompat>()
            )
        } just runs
        every {
            LocationManagerCompat.removeUpdates(any(), any<LocationListenerCompat>())
        } just runs
        repository = GpsRepositoryImpl(app)
    }

    @After
    fun tearDown() {
        unmockkStatic(LocationManagerCompat::class)
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state — not receiving updates, no location`() {
        assertFalse(repository.isReceivingUpdates.value)
        assertNull(repository.location.value)
    }

    // ── State transitions ────────────────────────────────────────────────────

    @Test
    fun `start sets isReceivingUpdates to true`() {
        repository.start()
        assertTrue(repository.isReceivingUpdates.value)
    }

    @Test
    fun `stop after start sets isReceivingUpdates to false`() {
        repository.start()
        repository.stop()
        assertFalse(repository.isReceivingUpdates.value)
    }

    @Test
    fun `start is idempotent — repeated calls register listener only once`() {
        repository.start()
        repository.start()
        verify(exactly = 1) {
            LocationManagerCompat.requestLocationUpdates(
                any(), any(), any<LocationRequestCompat>(), any<Executor>(), any<LocationListenerCompat>()
            )
        }
    }

    // ── Location data mapping ────────────────────────────────────────────────

    @Test
    fun `location update maps bearing and speed when available`() = runTest {
        val listenerSlot = slot<LocationListenerCompat>()
        every {
            LocationManagerCompat.requestLocationUpdates(
                any(), any(), any<LocationRequestCompat>(), any<Executor>(), capture(listenerSlot)
            )
        } just runs
        repository.start()

        val androidLoc = mockk<Location> {
            every { latitude } returns 55.75
            every { longitude } returns 37.62
            every { hasBearing() } returns true
            every { bearing } returns 270f
            every { hasSpeed() } returns true
            every { speed } returns 3f
            every { accuracy } returns 8f
            every { elapsedRealtimeNanos } returns 1_000_000L
            every { time } returns 1_700_000_000_000L
        }

        repository.location.test {
            awaitItem() // initial null
            listenerSlot.captured.onLocationChanged(androidLoc)
            val loc = awaitItem()!!
            assertEquals(55.75, loc.latitude, 0.0001)
            assertEquals(37.62, loc.longitude, 0.0001)
            assertEquals(270f, loc.bearing)
            assertEquals(3f, loc.speed)
            assertEquals(8f, loc.accuracy)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `location update — bearing and speed null when unavailable`() = runTest {
        val listenerSlot = slot<LocationListenerCompat>()
        every {
            LocationManagerCompat.requestLocationUpdates(
                any(), any(), any<LocationRequestCompat>(), any<Executor>(), capture(listenerSlot)
            )
        } just runs
        repository.start()

        val androidLoc = mockk<Location> {
            every { latitude } returns 55.0
            every { longitude } returns 37.0
            every { hasBearing() } returns false
            every { hasSpeed() } returns false
            every { accuracy } returns 20f
            every { elapsedRealtimeNanos } returns 2_000_000L
            every { time } returns 1_700_000_000_000L
        }

        repository.location.test {
            awaitItem() // initial null
            listenerSlot.captured.onLocationChanged(androidLoc)
            val loc = awaitItem()!!
            assertNull(loc.bearing)
            assertNull(loc.speed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
