package ru.tcynik.klitch.domain.marker.util

import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.mesh.common.util.latLongToMeter

object GeoTrackDistance {

    /** Distance in meters between the last two vertices; 0 when fewer than two points. */
    fun lastSegmentMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        val a = points[points.size - 2]
        val b = points[points.size - 1]
        return latLongToMeter(a.latitude, a.longitude, b.latitude, b.longitude)
    }

    /** Sum of consecutive segment lengths in meters; 0 when fewer than two points. */
    fun totalMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            total += latLongToMeter(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    fun formatKmRatio(segmentMeters: Double, totalMeters: Double): String =
        "${formatKm(segmentMeters)}/${formatKm(totalMeters)}km"

    private fun formatKm(meters: Double): String = "%.3f".format(meters / 1000.0)
}
