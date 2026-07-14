package ru.tcynik.klitch.data.track.kml

import org.junit.Assert.assertTrue
import org.junit.Test

class TrackKmlWriterTest {

    @Test
    fun `writes name and coordinates in lon,lat order`() {
        val track = testTrack(name = "Morning Hike")
        val points = listOf(
            testPoint(lat = 55.75, lon = 37.62),
            testPoint(lat = 55.76, lon = 37.63),
        )

        val kml = TrackKmlWriter.write(track, points)

        assertTrue(kml.contains("<name>Morning Hike</name>"))
        assertTrue(kml.contains("37.62,55.75,0"))
        assertTrue(kml.contains("37.63,55.76,0"))
    }

    @Test
    fun `escapes xml special characters in name`() {
        val kml = TrackKmlWriter.write(testTrack(name = "A & B <test>"), emptyList())

        assertTrue(kml.contains("<name>A &amp; B &lt;test&gt;</name>"))
    }
}
