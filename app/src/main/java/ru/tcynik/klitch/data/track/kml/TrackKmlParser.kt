package ru.tcynik.klitch.data.track.kml

import ru.tcynik.klitch.domain.track.model.ParsedTrack

object TrackKmlParser {

    private val nameRegex = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
    private val coordinatesRegex = Regex("<coordinates>(.*?)</coordinates>", RegexOption.DOT_MATCHES_ALL)

    /** Reads the first `<Placemark>/<LineString>` in the document; any others are ignored. */
    fun parse(kmlText: String): ParsedTrack? {
        val name = nameRegex.find(kmlText)?.groupValues?.get(1)?.unescapeXml().orEmpty()
        val coordinatesBlock = coordinatesRegex.find(kmlText)?.groupValues?.get(1) ?: return null
        val points = coordinatesBlock.trim().split(Regex("\\s+")).mapNotNull { tuple ->
            val parts = tuple.split(",")
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }
        if (points.isEmpty()) return null
        return ParsedTrack(name = name, points = points)
    }

    private fun String.unescapeXml() = this
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
