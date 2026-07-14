package ru.tcynik.klitch.data.marker.repository

import android.content.Context
import android.net.Uri
import ru.tcynik.klitch.data.track.kml.TrackKmlWriter
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.marker.mapper.MeshtasticPathTrackMapper
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository
import ru.tcynik.klitch.domain.marker.repository.MeshtasticPathFileRepository

private const val TAG = "Marker"

class MeshtasticPathFileRepositoryImpl(
    private val context: Context,
    private val geoMarkRepository: GeoMarkRepository,
    private val logger: Logger,
) : MeshtasticPathFileRepository {

    override suspend fun export(markId: String, destinationUri: String): Result<Unit> = runCatching {
        val mark = geoMarkRepository.getById(markId) ?: error("Mark not found: $markId")
        check(mark.type == GeoMarkType.TRACK) { "Not a track: $markId" }
        val (track, points) = MeshtasticPathTrackMapper.toRecordedTrack(mark)
        val kml = TrackKmlWriter.write(track, points)
        context.contentResolver.openOutputStream(Uri.parse(destinationUri))
            ?.use { it.write(kml.toByteArray()) }
            ?: error("Cannot open output stream for $destinationUri")
        logger.d(TAG, "Exported meshtastic path $markId, ${points.size} points")
    }.onFailure { e -> logger.e(TAG, "Export failed for mark $markId", e) }
}
