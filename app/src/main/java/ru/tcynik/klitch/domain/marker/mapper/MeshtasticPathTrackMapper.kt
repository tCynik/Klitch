package ru.tcynik.klitch.domain.marker.mapper

import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.model.TrackPoint
import ru.tcynik.klitch.domain.track.util.haversineMeters

/** Converts a manually-drawn MeshtasticPath (`GeoMarkModel`, `type = TRACK`) into an ephemeral
 *  `RecordedTrack` shape, never persisted, purely so it can be fed into the existing `TrackKmlWriter`. */
object MeshtasticPathTrackMapper {

    fun toRecordedTrack(mark: GeoMarkModel): Pair<RecordedTrack, List<TrackPoint>> {
        val distance = mark.points.zipWithNext()
            .sumOf { (a, b) -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
        val track = RecordedTrack(
            id = mark.id,
            name = mark.name,
            startedAt = mark.createdAt,
            finishedAt = mark.createdAt,
            totalDistanceMeters = distance,
            color = mark.color,
            isVisible = true,
            hasTimestamps = false,
        )
        val points = mark.points.mapIndexed { index, point ->
            TrackPoint(
                trackId = mark.id,
                timestampMs = mark.createdAt * 1_000 + index,
                lat = point.latitude,
                lon = point.longitude,
                accuracy = 0f,
            )
        }
        return track to points
    }
}
