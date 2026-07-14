package ru.tcynik.klitch.data.track.repository

import android.content.Context
import android.net.Uri
import ru.tcynik.klitch.data.track.kml.TrackKmlParser
import ru.tcynik.klitch.data.track.kml.TrackKmlWriter
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.repository.RecordedTrackRepository
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository
import java.util.zip.ZipInputStream

private const val TAG = "Track"

/** Local file header signature "PK\x03\x04" — used to tell a zipped KMZ apart from plain KML text. */
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

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
        val bytes = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?.use { it.readBytes() }
            ?: error("Cannot open input stream for $sourceUri")
        val text = if (bytes.startsWith(ZIP_MAGIC)) extractKmlFromKmz(bytes) else bytes.toString(Charsets.UTF_8)
        val parsed = TrackKmlParser.parse(text) ?: error("No LineString found in KML")
        trackRepository.insertImported(name = parsed.name, points = parsed.points)
    }.onFailure { e -> logger.e(TAG, "Import failed for $sourceUri", e) }

    /** KMZ is a zip archive; pulls the text of its first `.kml` entry (conventionally `doc.kml`). */
    private fun extractKmlFromKmz(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".kml", ignoreCase = true)) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        error("No .kml entry found in KMZ")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
