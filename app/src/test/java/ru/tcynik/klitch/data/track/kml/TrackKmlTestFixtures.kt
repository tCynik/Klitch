package ru.tcynik.klitch.data.track.kml

import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.model.TrackPoint

internal fun testTrack(name: String) = RecordedTrack(
    id = "t1",
    name = name,
    startedAt = 1_000L,
    finishedAt = 2_000L,
    totalDistanceMeters = 123.0,
    color = 0,
    isVisible = true,
    hasTimestamps = true,
)

internal fun testPoint(lat: Double, lon: Double) = TrackPoint(
    trackId = "t1",
    timestampMs = 1_000L,
    lat = lat,
    lon = lon,
    accuracy = 0f,
)
