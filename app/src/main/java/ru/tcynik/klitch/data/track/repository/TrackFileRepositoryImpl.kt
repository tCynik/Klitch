package ru.tcynik.klitch.data.track.repository

import android.content.Context
import android.net.Uri
import ru.tcynik.klitch.data.track.kml.TrackKmlParser
import ru.tcynik.klitch.data.track.kml.TrackKmlWriter
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository

private const val TAG = "Track"

class TrackFileRepositoryImpl(
    private val context: Context,
    private val trackRepository: RecordedTrackRepository,
    private val logger: Logger,
) : TrackFileRepository {

    override suspend fun export(trackId: String, destinationUri: String): Result<Unit> = runCatching {
        val track = trackRepository.getById(trackId) ?: error("Track not found: $trackId")
        check(track.finishedAt != null) { "Cannot export an unfinished track: $trackId" }
        val points = trackRepository.getPoints(trackId)
        val kml = TrackKmlWriter.write(track, points)
        context.contentResolver.openOutputStream(Uri.parse(destinationUri))
            ?.use { it.write(kml.toByteArray()) }
            ?: error("Cannot open output stream for $destinationUri")
        logger.d(TAG, "Exported track $trackId, ${points.size} points")
    }.onFailure { e -> logger.e(TAG, "Export failed for track $trackId", e) }

    override suspend fun import(sourceUri: String): Result<RecordedTrack> = runCatching {
        val text = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?.use { it.bufferedReader().readText() }
            ?: error("Cannot open input stream for $sourceUri")
        val parsed = TrackKmlParser.parse(text) ?: error("No LineString found in KML")
        trackRepository.insertImported(name = parsed.name, points = parsed.points)
    }.onFailure { e -> logger.e(TAG, "Import failed for $sourceUri", e) }
}
