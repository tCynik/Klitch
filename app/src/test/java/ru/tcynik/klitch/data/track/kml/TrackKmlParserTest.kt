package ru.tcynik.klitch.data.track.kml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackKmlParserTest {

    @Test
    fun `round-trips writer output`() {
        val track = testTrack(name = "Loop")
        val points = listOf(
            testPoint(lat = 55.75, lon = 37.62),
            testPoint(lat = 55.76, lon = 37.63),
        )
        val kml = TrackKmlWriter.write(track, points)

        val parsed = TrackKmlParser.parse(kml)

        assertEquals("Loop", parsed?.name)
        assertEquals(listOf(55.75 to 37.62, 55.76 to 37.63), parsed?.points)
    }

    @Test
    fun `returns null when no LineString present`() {
        val kml = """<kml><Placemark><name>No coords</name></Placemark></kml>"""

        assertNull(TrackKmlParser.parse(kml))
    }

    @Test
    fun `uses only the first Placemark when multiple are present`() {
        val kml = """
            <kml>
              <Placemark><name>First</name><LineString><coordinates>10,20,0 11,21,0</coordinates></LineString></Placemark>
              <Placemark><name>Second</name><LineString><coordinates>30,40,0</coordinates></LineString></Placemark>
            </kml>
        """.trimIndent()

        val parsed = TrackKmlParser.parse(kml)

        assertEquals("First", parsed?.name)
        assertEquals(listOf(20.0 to 10.0, 21.0 to 11.0), parsed?.points)
    }
}
