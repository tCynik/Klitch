package ru.tcynik.klitch.domain.marker.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.domain.track.util.haversineMeters

class MeshtasticPathTrackMapperTest {

    private val mark = GeoMarkModel(
        id = "mark-1",
        waypointId = 0,
        type = GeoMarkType.TRACK,
        points = listOf(
            GeoPoint(latitude = 55.0, longitude = 37.0),
            GeoPoint(latitude = 55.001, longitude = 37.001),
            GeoPoint(latitude = 55.002, longitude = 37.002),
        ),
        authorNodeId = "",
        createdAt = 1_700_000_000L,
        expiresAt = null,
        isSelf = true,
        color = 5,
        name = "Test Path",
    )

    @Test
    fun `maps mark fields onto ephemeral RecordedTrack`() {
        val (track, _) = MeshtasticPathTrackMapper.toRecordedTrack(mark)

        assertEquals(mark.id, track.id)
        assertEquals(mark.name, track.name)
        assertEquals(mark.createdAt, track.startedAt)
        assertEquals(mark.createdAt, track.finishedAt)
        assertEquals(mark.color, track.color)
        assertEquals(true, track.isVisible)
        assertEquals(false, track.hasTimestamps)
    }

    @Test
    fun `computes total distance via haversine over consecutive points`() {
        val (track, _) = MeshtasticPathTrackMapper.toRecordedTrack(mark)

        val expected = mark.points.zipWithNext()
            .sumOf { (a, b) -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
        assertEquals(expected, track.totalDistanceMeters, 0.0001)
    }

    @Test
    fun `builds one TrackPoint per mark point with synthetic sequential timestamps`() {
        val (_, points) = MeshtasticPathTrackMapper.toRecordedTrack(mark)

        assertEquals(mark.points.size, points.size)
        points.forEachIndexed { index, point ->
            assertEquals(mark.id, point.trackId)
            assertEquals(mark.createdAt * 1_000 + index, point.timestampMs)
            assertEquals(mark.points[index].latitude, point.lat, 0.0)
            assertEquals(mark.points[index].longitude, point.lon, 0.0)
            assertEquals(0f, point.accuracy)
        }
    }
}
