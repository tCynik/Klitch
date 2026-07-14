package ru.tcynik.klitch.data.track.kml

import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.model.TrackPoint

object TrackKmlWriter {

    fun write(track: RecordedTrack, points: List<TrackPoint>): String {
        val coordinates = points.joinToString(separator = " ") { "${it.lon},${it.lat},0" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>${track.name.escapeXml()}</name>
                <LineString>
                  <coordinates>$coordinates</coordinates>
                </LineString>
              </Placemark>
            </kml>
        """.trimIndent()
    }

    private fun String.escapeXml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
