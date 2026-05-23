package ru.tcynik.meshtactics.domain.marker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint

class GeoTrackDistanceTest {

    @Test
    fun `lastSegmentMeters — zero for fewer than two points`() {
        assertEquals(0.0, GeoTrackDistance.lastSegmentMeters(emptyList()), 0.001)
        assertEquals(0.0, GeoTrackDistance.lastSegmentMeters(listOf(GeoPoint(0.0, 0.0))), 0.001)
    }

    @Test
    fun `totalMeters — equals last segment for two points, doubles for three collinear`() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(0.0, 0.009)
        val c = GeoPoint(0.0, 0.018)
        val two = listOf(a, b)
        val three = listOf(a, b, c)
        val segTwo = GeoTrackDistance.lastSegmentMeters(two)
        val totalTwo = GeoTrackDistance.totalMeters(two)
        assertEquals(segTwo, totalTwo, 0.001)
        val segThree = GeoTrackDistance.lastSegmentMeters(three)
        val totalThree = GeoTrackDistance.totalMeters(three)
        assertEquals(segTwo, segThree, segTwo * 0.02)
        assertEquals(segTwo * 2, totalThree, totalThree * 0.02)
    }
}
